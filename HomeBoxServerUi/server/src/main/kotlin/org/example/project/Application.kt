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
import io.ktor.server.auth.session
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.AdminPasswordArgon2.ADMIN_USERNAME
import org.example.project.searchData.SearchCompleteItem
import org.example.project.serverData.DeleteItem
import org.example.project.serverData.DeleteResponse
import org.example.project.serverData.DownloadItem
import org.example.project.serverData.ServerSettings
import org.slf4j.event.Level
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours


fun main() {
    val isProd = (System.getenv(APP_ENV) ?: DEV_ENV).lowercase() == PROD_ENV

    // În PROD, nu expune Ktor direct. Lasă-l local și pune Caddy/Nginx în față.
    val host = if (isProd) "127.0.0.1" else "0.0.0.0"
    val serverPort = 8085
    val appName = SERVER_NAME

    val home = System.getProperty("user.home")
    val envDir = Path.of(home, ".config", appName)
    val envFile = envDir.resolve("env")

    val kv = ShellEnvFileKvStore(envFile)
    kv.ensureDirAndUmaskLikePermissions()

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
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost() // Allows any origin. Adjust this in production.
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowCredentials = true
    }
    install(CallLogging) { level = Level.INFO }

    val encryptKeyHex = requireEnv(SESSION_ENCRYPT_KEY_HEX)
    val signKeyHex = requireEnv(SESSION_SIGN_KEY_HEX)
    val isProd = (System.getenv(APP_ENV) ?: DEV_ENV).lowercase() == PROD_ENV

    install(Sessions) {
        cookie<AdminSession>("admin_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure =
                isProd   // în dev pe http trebuie false, altfel browserul NU salvează cookie-ul
            cookie.maxAgeInSeconds = 12.hours.inWholeSeconds

            // SameSite (Ktor: via extensions). :contentReference[oaicite:3]{index=3}
            cookie.extensions["SameSite"] = if (isProd) "Strict" else "Lax"

            // Semnează + criptează conținutul sesiunii în cookie
            transform(SessionTransportTransformerEncrypt(hex(encryptKeyHex), hex(signKeyHex)))
        }
    }
    install(Authentication) {
        session<AdminSession>(ADMIN_SESSION) {
            validate { sess ->
                if (sess.user == requireEnv(ADMIN_USERNAME)) UserIdPrincipal(sess.user) else null
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Login required")
            }
        }
    }

    val filelistClient = FilelistClient()
    SettingsEncriptor.createServerSettingsIfNotExist()
    FileManager.configureExistingBase(SettingsEncriptor.readSettingsFromFile().downloadFolder)

    routing {

        get("/get") {
            call.respondText("Server is running!", ContentType.Text.Plain)
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            if (verifyAdminCredentials(req.username, req.password)) {
                call.sessions.set(
                    AdminSession(
                        user = req.username,
                        issuedAtMillis = System.currentTimeMillis()
                    )
                )
                call.respond(LoginResponse(true))
            } else {
                call.respond(LoginResponse(false))
            }
        }

        post("/loginFileList") {
            val loginRequest = call.receive<LoginRequest>()

            // First, attempt login
            val success = filelistClient.login(loginRequest.username, loginRequest.password)
            call.respond(HttpStatusCode.OK, LoginResponse(success))
        }

        get("/firstSearch") {
            println("first search")
            val results = filelistClient.firstSearch()
            call.respond(HttpStatusCode.OK, results)
        }

        post("/search") {
            println("search")
            val searchItem = call.receive<SearchCompleteItem>()
            println(searchItem)
            val results = filelistClient.search(searchItem)
            call.respond(HttpStatusCode.OK, results)
        }

        get("/diskSpace") {
            val usage = withContext(Dispatchers.IO) { getAllDiskUsage() }
            call.respond(usage)
        }

        get("/dirData") {
            val usage = withContext(Dispatchers.IO) { FileManager.list() }
            call.respond(usage)
        }

        post("/deleteFile") {
            val fileName = call.receive<DeleteItem>()
            println("delete file: ${fileName}")
            val deleted = withContext(Dispatchers.IO) { FileManager.deleteFile(fileName.item) }
            call.respond(DeleteResponse(deleted.ok))
        }

        get("/qBittorrentRunning") {
            val isInstalled =
                withContext(Dispatchers.IO) { QBittorrentUtils.isQbittorrentRunning() }
            call.respond(isInstalled)
        }

        post("/downloadFile") {
            val item = call.receive<DownloadItem>()
            val response =
                withContext(Dispatchers.IO) { filelistClient.downloadFile(item.itemId) }
            call.respond(response)
        }

        get("/torrentsStatus") {
            val torrents =
                withContext(Dispatchers.IO) { QBittorrentUtils.getTorrentsList() }
            call.respond(torrents)
        }

        post("/deleteTorrent") {
            val hash = call.receive<String>()
            val response = withContext(Dispatchers.IO) { QBittorrentUtils.deleteTorrent(hash) }
            call.respond(response)
        }

        get("/getServerSettings") {
            val response = SettingsEncriptor.readSettingsFromFile()
            call.respond(response)
        }

        post("/saveServerSettings") {
            val settings = call.receive<ServerSettings>()
            val response =
                withContext(Dispatchers.IO) { SettingsEncriptor.saveSettingsToFile(settings) }
            call.respond(response)
        }
    }
}

private fun verifyAdminCredentials(username: String, password: String): Boolean {
    val expectedUser = requireEnv(AdminPasswordArgon2.ADMIN_USERNAME)
    val expectedHash = requireEnv(AdminPasswordArgon2.KEY)

    if (username != expectedUser) return false
    return AdminPasswordArgon2.verifyPassword(expectedHash, password.toCharArray())
}

fun requireEnv(name: String): String = System.getenv(name)
    ?: error("Missing env var: $name")

private fun getEnv(name: String): String? = System.getenv(name)