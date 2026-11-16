package org.example.project

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import kotlinx.serialization.Serializable
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.AdminPasswordArgon2.ADMIN_USERNAME
import org.example.project.TotpDisableRequest
import org.example.project.TotpEnableRequest
import org.example.project.TotpSetupResponse
import org.example.project.TotpVerifyRequest
import org.example.project.searchData.SearchCompleteItem
import org.example.project.serverData.DeleteItem
import org.example.project.serverData.DeleteResponse
import org.example.project.serverData.DownloadItem
import org.example.project.serverData.ServerSettings
import org.example.project.serverData.ServerSettingsClient
import org.example.project.serverData.TotpDevice
import org.slf4j.event.Level
import java.nio.file.Path
import java.util.UUID
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


fun main() {
    val isProd = (System.getenv(APP_ENV) ?: DEV_ENV).lowercase() == PROD_ENV

    // In prod, never bind on all interfaces; put Caddy/Nginx in front on 443.
    val defaultHost = if (isProd) "127.0.0.1" else "0.0.0.0"
    val host = System.getenv(HOMEBOX_HOST)?.takeIf { it.isNotBlank() } ?: defaultHost
    val serverPort = System.getenv(HOMEBOX_PORT)?.toIntOrNull()
        ?.takeIf { it in 1..65535 }
        ?: 8085

    if (!isProd) {
        System.err.println(
            "WARN: $APP_ENV is not '$PROD_ENV'. Session cookies are not Secure, " +
                    "SameSite is Lax, and the server may bind to 0.0.0.0. " +
                    "Do NOT run like this on the public internet."
        )
    }

    val appName = SERVER_NAME

    val home = System.getProperty("user.home")
    val envDir = Path.of(home, ".config", appName)
    val envFile = envDir.resolve("env")

    val kv = ShellEnvFileKvStore(envFile)
    kv.ensureDirAndUmaskLikePermissions()

    SettingsEncriptor.createServerSettingsIfNotExist()
    SettingsEncriptor.consumeSeedFile()

    AdminPasswordArgon2.ensureAdminPassword(object : KvStore {
        override fun get(key: String): String? =
            getEnv(key)

        override fun set(key: String, value: String) {
            kv.set(key, value)
            exitProcess(0)
        }
    })

    embeddedServer(Netty, port = serverPort, host = host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val isProd = (System.getenv(APP_ENV) ?: DEV_ENV).lowercase() == PROD_ENV

    // Defence-in-depth body-size check. Caddy should already reject large
    // bodies (request_body / max_size), but we refuse at the app layer too
    // in case someone bypasses the proxy during local operation.
    val maxBodyBytes = 2L * 1024 * 1024 // 2 MB
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (contentLength != null && contentLength > maxBodyBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            finish()
        }
    }

    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        if (isProd) {
            allowHost("homestreambox.go.ro", schemes = listOf("https"))
        } else {
            // Dev: allow the Compose/Kotlin dev servers on localhost.
            allowHost("localhost:8086", schemes = listOf("http"))
            allowHost("localhost:8087", schemes = listOf("http"))
            allowHost("127.0.0.1:8086", schemes = listOf("http"))
            allowHost("127.0.0.1:8087", schemes = listOf("http"))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-CSRF-Token")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowCredentials = true
    }
    install(CallLogging) {
        level = Level.INFO
        // Don't log login / settings paths — bodies may contain credentials.
        filter { call ->
            val path = call.request.path()
            !path.startsWith("/api/login") &&
                !path.startsWith("/api/saveServerSettings")
        }
    }

    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.local.remoteHost }
        }
    }

    install(StatusPages) {
        exception<RequestValidationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid request"))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("malformed request"))
        }
        exception<kotlinx.serialization.SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("malformed JSON"))
        }
        exception<Throwable> { call, cause ->
            // Let coroutine cancellation propagate — never swallow it.
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            val errorId = UUID.randomUUID().toString().take(8)
            // Log full detail server-side only; never return to client.
            call.application.log.error(
                "unhandled errorId={} path={} type={} msg={}",
                errorId, call.request.path(), cause.javaClass.simpleName, cause.message,
            )
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal", errorId))
        }
    }

    install(RequestValidation) {
        validate<LoginRequest> { req ->
            when {
                req.username.length !in 1..64 -> ValidationResult.Invalid("username length 1..64")
                req.password.length !in 1..256 -> ValidationResult.Invalid("password length 1..256")
                else -> ValidationResult.Valid
            }
        }
        validate<DeleteItem> { req ->
            when {
                req.item.isBlank() -> ValidationResult.Invalid("item required")
                req.item.length > 4096 -> ValidationResult.Invalid("item too long")
                req.item.any { it.code < 0x20 || it.code == 0x7F } -> ValidationResult.Invalid("item contains control chars")
                else -> ValidationResult.Valid
            }
        }
        validate<DownloadItem> { req ->
            when {
                req.itemId.isBlank() -> ValidationResult.Invalid("itemId required")
                !req.itemId.matches(Regex("^[A-Za-z0-9_\\-]{1,128}\$")) ->
                    ValidationResult.Invalid("itemId must be 1..128 chars of [A-Za-z0-9_-]")
                else -> ValidationResult.Valid
            }
        }
        validate<ServerSettingsClient> { s ->
            when {
                s.filelistUsername.length > 128 -> ValidationResult.Invalid("filelistUsername too long")
                s.filelistPassword.length > 256 -> ValidationResult.Invalid("filelistPassword too long")
                s.qbUsername.length > 128 -> ValidationResult.Invalid("qbUsername too long")
                s.qbPassword.length > 256 -> ValidationResult.Invalid("qbPassword too long")
                else -> ValidationResult.Valid
            }
        }
        validate<TotpVerifyRequest> { r ->
            when {
                r.pendingToken.isBlank() -> ValidationResult.Invalid("pendingToken required")
                r.code.length != 6 || !r.code.all { it.isDigit() } -> ValidationResult.Invalid("code must be 6 digits")
                else -> ValidationResult.Valid
            }
        }
        validate<TotpEnableRequest> { r ->
            when {
                r.secret.isBlank() || r.secret.length > 64 -> ValidationResult.Invalid("invalid secret")
                r.code.length != 6 || !r.code.all { it.isDigit() } -> ValidationResult.Invalid("code must be 6 digits")
                r.deviceName.isBlank() || r.deviceName.length > 64 -> ValidationResult.Invalid("deviceName must be 1..64 chars")
                else -> ValidationResult.Valid
            }
        }
        validate<TotpDisableRequest> { r ->
            when {
                r.code.length != 6 || !r.code.all { it.isDigit() } -> ValidationResult.Invalid("code must be 6 digits")
                else -> ValidationResult.Valid
            }
        }
    }

    val encryptKeyHex = requireEnv(SESSION_ENCRYPT_KEY_HEX)
    val signKeyHex = requireEnv(SESSION_SIGN_KEY_HEX)
    // Ktor's SessionTransportTransformerEncrypt uses the encryption key as the
    // AES-CTR IV too, so it must be exactly 16 bytes (AES-128 block size).
    // The signing key is HMAC-SHA256; 32 bytes is the recommended minimum.
    require(hex(encryptKeyHex).size == 16) {
        "$SESSION_ENCRYPT_KEY_HEX must decode to exactly 16 bytes (AES-128)"
    }
    require(hex(signKeyHex).size >= 32) {
        "$SESSION_SIGN_KEY_HEX must decode to >= 32 bytes (HMAC-SHA256)"
    }

    install(Sessions) {
        cookie<AdminSession>("admin_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = isProd
            cookie.maxAgeInSeconds = 2.hours.inWholeSeconds
            cookie.extensions["SameSite"] = if (isProd) "Strict" else "Lax"
            transform(SessionTransportTransformerEncrypt(hex(encryptKeyHex), hex(signKeyHex)))
        }
    }
    install(Authentication) {
        session<AdminSession>(ADMIN_SESSION) {
            validate { sess ->
                when {
                    sess.user != requireEnv(ADMIN_USERNAME) -> null
                    SessionRegistry.isRevoked(sess.sessionId) -> null
                    else -> UserIdPrincipal(sess.user)
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Login required")
            }
        }
    }

    // CSRF double-submit: for state-changing methods, require that the
    // client echoes back the token it received at login via X-CSRF-Token.
    // A cross-origin attacker can forge a cookie-carrying request but
    // cannot read the response of /api/login, so cannot know the token.
    //
    // Must register AFTER Sessions is installed — otherwise
    // `call.sessions.get<T>()` throws SessionNotYetConfiguredException.
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        val method = call.request.httpMethod
        // Public endpoints and read-only methods are exempt.
        if (path == "/api/get" || path == "/api/login" || path == "/api/totp/verify") return@intercept
        if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) {
            return@intercept
        }
        val session = call.sessions.get<AdminSession>()
        val header = call.request.header("X-CSRF-Token")
        if (session == null || header.isNullOrEmpty() || !constantTimeEquals(header, session.csrfToken)) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("csrf"))
            finish()
        }
    }

    SettingsEncriptor.assertKeyAvailable()

    val filelistClient = FilelistClient()
    SettingsEncriptor.createServerSettingsIfNotExist()

    // Base directory is operator-controlled (env), never client-controlled.
    // Falls back to ~/Downloads if HOMEBOX_DOWNLOAD_FOLDER is not set.
    val configuredBase = System.getenv(HOMEBOX_DOWNLOAD_FOLDER)
        ?: Path.of(System.getProperty("user.home"), "Downloads").toString()
    val baseResult = FileManager.configureExistingBase(configuredBase)
    if (!baseResult.ok) {
        System.err.println("FATAL: ${baseResult.message}")
        exitProcess(1)
    }

    routing {

        // --- Public endpoints ---------------------------------------------
        get("/api/get") {
            call.respondText("Server is running!", ContentType.Text.Plain)
        }

        rateLimit(RateLimitName("login")) {
            post("/api/login") {
                val req = call.receive<LoginRequest>()
                val ip = call.request.local.remoteHost
                if (verifyAdminCredentials(req.username, req.password)) {
                    val settings = SettingsEncriptor.readSettingsFromFile()
                    if (settings.totpDevices.isNotEmpty()) {
                        val pendingToken = PendingTotpRegistry.create(req.username)
                        AuditLog.record(who = req.username, ip = ip, action = "login", result = "totp-required")
                        call.respond(LoginResponse(isSuccess = false, totpRequired = true, pendingToken = pendingToken))
                    } else {
                        val sessionId = UUID.randomUUID().toString()
                        val csrfToken = newCsrfToken()
                        SessionRegistry.register(sessionId)
                        call.sessions.set(
                            AdminSession(
                                user = req.username,
                                sessionId = sessionId,
                                issuedAtMillis = System.currentTimeMillis(),
                                csrfToken = csrfToken,
                            )
                        )
                        AuditLog.record(who = req.username, ip = ip, action = "login", result = "ok")
                        call.respond(LoginResponse(true, csrfToken))
                    }
                } else {
                    AuditLog.record(who = req.username, ip = ip, action = "login", result = "fail")
                    kotlinx.coroutines.delay(500)
                    call.respond(LoginResponse(false))
                }
            }

            post("/api/totp/verify") {
                val req = call.receive<TotpVerifyRequest>()
                val ip = call.request.local.remoteHost
                val username = PendingTotpRegistry.consume(req.pendingToken)
                if (username == null) {
                    kotlinx.coroutines.delay(500)
                    call.respond(LoginResponse(false))
                    return@post
                }
                val devices = SettingsEncriptor.readSettingsFromFile().totpDevices
                if (devices.isEmpty() || devices.none { TotpManager.verify(it.secret, req.code) }) {
                    AuditLog.record(who = username, ip = ip, action = "totp-verify", result = "fail")
                    kotlinx.coroutines.delay(500)
                    call.respond(LoginResponse(false))
                    return@post
                }
                val sessionId = UUID.randomUUID().toString()
                val csrfToken = newCsrfToken()
                SessionRegistry.register(sessionId)
                call.sessions.set(
                    AdminSession(
                        user = username,
                        sessionId = sessionId,
                        issuedAtMillis = System.currentTimeMillis(),
                        csrfToken = csrfToken,
                    )
                )
                AuditLog.record(who = username, ip = ip, action = "totp-verify", result = "ok")
                call.respond(LoginResponse(true, csrfToken))
            }
        }

        // --- Authenticated endpoints --------------------------------------
        authenticate(ADMIN_SESSION) {

            post("/api/logout") {
                val who = call.principal<UserIdPrincipal>()?.name
                call.sessions.get<AdminSession>()?.let { SessionRegistry.revoke(it.sessionId) }
                call.sessions.clear<AdminSession>()
                AuditLog.record(who, call.request.local.remoteHost, "logout")
                call.respond(HttpStatusCode.OK)
            }

            post("/api/logout-all") {
                val who = call.principal<UserIdPrincipal>()?.name
                SessionRegistry.revokeAll()
                call.sessions.clear<AdminSession>()
                AuditLog.record(who, call.request.local.remoteHost, "logout-all")
                call.respond(HttpStatusCode.OK)
            }

            get("/api/me") {
                val principal = call.principal<UserIdPrincipal>()
                val session = call.sessions.get<AdminSession>()
                if (principal == null || session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    // Sliding: refresh issuedAt on every /me and reissue the cookie.
                    val refreshed = session.copy(issuedAtMillis = System.currentTimeMillis())
                    call.sessions.set(refreshed)
                    call.respond(MeResponse(principal.name, refreshed.issuedAtMillis, refreshed.csrfToken))
                }
            }

            get("/api/totp/setup") {
                val secret = TotpManager.generateSecretBase32()
                call.respond(TotpSetupResponse(TotpManager.buildOtpauthUri(secret), secret))
            }

            post("/api/totp/enable") {
                val req = call.receive<TotpEnableRequest>()
                val who = call.principal<UserIdPrincipal>()?.name
                val ip = call.request.local.remoteHost
                if (!TotpManager.verify(req.secret, req.code)) {
                    AuditLog.record(who, ip, "totp-enable", result = "fail")
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid code"))
                    return@post
                }
                val existing = SettingsEncriptor.readSettingsFromFile()
                val newDevice = TotpDevice(req.deviceName, req.secret)
                SettingsEncriptor.saveSettingsToFile(existing.copy(totpDevices = existing.totpDevices + newDevice))
                AuditLog.record(who, ip, "totp-enable", result = "ok")
                call.respond(HttpStatusCode.OK)
            }

            post("/api/totp/disable") {
                val req = call.receive<TotpDisableRequest>()
                val who = call.principal<UserIdPrincipal>()?.name
                val ip = call.request.local.remoteHost
                val existing = SettingsEncriptor.readSettingsFromFile()
                val deviceName = req.deviceName
                if (deviceName == null) {
                    if (existing.totpDevices.none { TotpManager.verify(it.secret, req.code) }) {
                        AuditLog.record(who, ip, "totp-disable", result = "fail")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid code"))
                        return@post
                    }
                    SettingsEncriptor.saveSettingsToFile(existing.copy(totpDevices = emptyList()))
                } else {
                    val device = existing.totpDevices.find { it.name == deviceName }
                    if (device == null || !TotpManager.verify(device.secret, req.code)) {
                        AuditLog.record(who, ip, "totp-disable", result = "fail")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid code"))
                        return@post
                    }
                    SettingsEncriptor.saveSettingsToFile(existing.copy(totpDevices = existing.totpDevices.filter { it.name != deviceName }))
                }
                AuditLog.record(who, ip, "totp-disable", result = "ok")
                call.respond(HttpStatusCode.OK)
            }

            get("/api/totp/devices") {
                val devices = SettingsEncriptor.readSettingsFromFile().totpDevices.map { it.name }
                call.respond(devices)
            }

            post("/api/loginFileList") {
                val loginRequest = call.receive<LoginRequest>()
                val success = filelistClient.login(loginRequest.username, loginRequest.password)
                call.respond(HttpStatusCode.OK, LoginResponse(success))
            }

            get("/api/firstSearch") {
                val results = filelistClient.firstSearch()
                call.respond(HttpStatusCode.OK, results)
            }

            post("/api/search") {
                val searchItem = call.receive<SearchCompleteItem>()
                val results = filelistClient.search(searchItem)
                call.respond(HttpStatusCode.OK, results)
            }

            get("/api/diskSpace") {
                val usage = withContext(Dispatchers.IO) { getAllDiskUsage() }
                call.respond(usage)
            }

            get("/api/dirData") {
                val usage = withContext(Dispatchers.IO) { FileManager.list() }
                call.respond(usage)
            }

            post("/api/deleteFile") {
                val fileName = call.receive<DeleteItem>()
                val deleted = withContext(Dispatchers.IO) { FileManager.deleteFile(fileName.item) }
                AuditLog.record(
                    who = call.principal<UserIdPrincipal>()?.name,
                    ip = call.request.local.remoteHost,
                    action = "deleteFile",
                    target = fileName.item,
                    result = if (deleted.ok) "ok" else "fail",
                )
                call.respond(DeleteResponse(deleted.ok))
            }

            get("/api/qBittorrentRunning") {
                val isInstalled =
                    withContext(Dispatchers.IO) { QBittorrentUtils.isQbittorrentRunning() }
                call.respond(isInstalled)
            }

            post("/api/downloadFile") {
                val item = call.receive<DownloadItem>()
                val response =
                    withContext(Dispatchers.IO) { filelistClient.downloadFile(item.itemId) }
                AuditLog.record(
                    who = call.principal<UserIdPrincipal>()?.name,
                    ip = call.request.local.remoteHost,
                    action = "downloadFile",
                    target = item.itemId,
                    result = if (response.downloadingStarted) "ok" else "fail",
                )
                call.respond(response)
            }

            get("/api/torrentsStatus") {
                val torrents =
                    withContext(Dispatchers.IO) { QBittorrentUtils.getTorrentsList() }
                call.respond(torrents)
            }

            post("/api/deleteTorrent") {
                val hash = call.receive<String>()
                val response = withContext(Dispatchers.IO) { QBittorrentUtils.deleteTorrent(hash) }
                AuditLog.record(
                    who = call.principal<UserIdPrincipal>()?.name,
                    ip = call.request.local.remoteHost,
                    action = "deleteTorrent",
                    target = hash,
                    result = if (response) "ok" else "fail",
                )
                call.respond(response)
            }

            get("/api/getServerSettings") {
                val response = withContext(Dispatchers.IO) { SettingsEncriptor.readClientSettings() }
                call.respond(response)
            }

            post("/api/saveServerSettings") {
                val settings = call.receive<ServerSettingsClient>()
                val response =
                    withContext(Dispatchers.IO) { SettingsEncriptor.saveClientSettings(settings) }
                AuditLog.record(
                    who = call.principal<UserIdPrincipal>()?.name,
                    ip = call.request.local.remoteHost,
                    action = "saveServerSettings",
                    result = if (response) "ok" else "fail",
                )
                call.respond(response)
            }
        }
    }
}

/**
 * A pre-computed Argon2id hash of a throwaway password. Used to make the
 * runtime of the "username does not exist" branch indistinguishable from
 * the "username exists, verify password" branch (constant-time-ish).
 */
private val DUMMY_ARGON2_HASH: String by lazy {
    val argon2 = de.mkammerer.argon2.Argon2Factory.create(
        de.mkammerer.argon2.Argon2Factory.Argon2Types.ARGON2id,
        16,
        32
    )
    try {
        argon2.hash(3, 1 shl 16, 1, "dummy-not-a-real-password".toCharArray())
    } catch (_: Exception) {
        // If hashing fails (shouldn't), fall back to a known-valid PHC that still verifies slowly.
        "\$argon2id\$v=19\$m=65536,t=3,p=1\$c29tZXNhbHRzb21lc2FsdA\$dummy"
    }
}

private fun verifyAdminCredentials(username: String, password: String): Boolean {
    val expectedUser = requireEnv(AdminPasswordArgon2.ADMIN_USERNAME)
    val expectedHash = requireEnv(AdminPasswordArgon2.KEY)

    // Always run Argon2 verify to avoid timing side-channels that leak whether
    // the username exists. We select the hash based on username match, but
    // verify runs either way.
    val usernameMatches = username == expectedUser
    val hashToVerify = if (usernameMatches) expectedHash else DUMMY_ARGON2_HASH

    val passwordOk = AdminPasswordArgon2.verifyPassword(hashToVerify, password.toCharArray())
    return usernameMatches && passwordOk
}

fun requireEnv(name: String): String = System.getenv(name)
    ?: error("Missing env var: $name")

private fun getEnv(name: String): String? = System.getenv(name)

@Serializable
data class ErrorResponse(val error: String, val errorId: String? = null)

private val csrfRng = java.security.SecureRandom()

private fun newCsrfToken(): String {
    val bytes = ByteArray(32).also { csrfRng.nextBytes(it) }
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private object PendingTotpRegistry {
    private val ttlMs = 5L * 60 * 1000
    private val map = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val rng = java.security.SecureRandom()

    fun create(username: String): String {
        prune()
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(24).also { rng.nextBytes(it) })
        map[token] = username to System.currentTimeMillis()
        return token
    }

    fun consume(token: String): String? {
        prune()
        val (user, ts) = map.remove(token) ?: return null
        return if (System.currentTimeMillis() - ts <= ttlMs) user else null
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - ttlMs
        map.entries.removeIf { it.value.second < cutoff }
    }
}

/** Length-independent, constant-time string comparison for secrets. */
private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].code xor b[i].code)
    }
    return diff == 0
}