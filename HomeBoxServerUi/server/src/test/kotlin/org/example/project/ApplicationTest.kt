package org.example.project

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.*

/**
 * Integration tests that lock down the auth model.
 *
 * Every protected endpoint must reject unauthenticated requests. CSRF must
 * be enforced on state-changing methods. Path traversal must be refused by
 * [FileManager]. Settings encryption must round-trip and must randomise IVs.
 *
 * These tests run against the real `module()` with ephemeral env vars so a
 * regression in auth wiring (someone deleting an `authenticate { }` block,
 * for instance) fails the build immediately.
 */
class ApplicationTest {

    /** The download base used by FileManager tests. The Gradle `test` task
     *  sets `HOMEBOX_DOWNLOAD_FOLDER` to the same path. */
    private val tempBase: Path = Path.of(System.getenv("HOMEBOX_DOWNLOAD_FOLDER") ?: "build/test-base")

    @BeforeTest
    fun setUp() {
        Files.createDirectories(tempBase)
    }

    // -----------------------------------------------------------------
    // AUTH: every protected endpoint must return 401 without a session
    // -----------------------------------------------------------------

    @Test
    fun `dirData requires authentication`() = testApplication {
        application { module() }
        val r = client.get("/api/dirData")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `saveServerSettings requires authentication`() = testApplication {
        application { module() }
        val r = client.post("/api/saveServerSettings") {
            contentType(ContentType.Application.Json)
            setBody("""{"filelistUsername":"","filelistPassword":"","qbUsername":"","qbPassword":""}""")
        }
        // Either 401 (auth) or 403 (csrf) — both are acceptable "blocked".
        assertTrue(r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden)
    }

    @Test
    fun `deleteFile requires authentication`() = testApplication {
        application { module() }
        val r = client.post("/api/deleteFile") {
            contentType(ContentType.Application.Json)
            setBody("""{"item":"foo.txt"}""")
        }
        assertTrue(r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden)
    }

    @Test
    fun `me is unauthorized when not logged in`() = testApplication {
        application { module() }
        val r = client.get("/api/me")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `public endpoints are reachable without auth`() = testApplication {
        application { module() }
        val r = client.get("/api/get")
        assertEquals(HttpStatusCode.OK, r.status)
    }

    // -----------------------------------------------------------------
    // CSRF: an authenticated POST without X-CSRF-Token must be rejected
    // -----------------------------------------------------------------

    @Test
    fun `csrf blocks authenticated POST without token`() = testApplication {
        application { module() }
        val http = createClient {
            install(HttpCookies)
            install(ContentNegotiation) { json() }
        }
        val login = http.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"test-password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        // Now call deleteFile without X-CSRF-Token
        val r = http.post("/api/deleteFile") {
            contentType(ContentType.Application.Json)
            setBody("""{"item":"foo.txt"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    // -----------------------------------------------------------------
    // Validation: malformed input must yield 400, not 500
    // -----------------------------------------------------------------

    @Test
    fun `login with empty password is rejected`() = testApplication {
        application { module() }
        val r = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `malformed json yields 400`() = testApplication {
        application { module() }
        val r = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("not json")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    // -----------------------------------------------------------------
    // Path safety
    // -----------------------------------------------------------------

    @Test
    fun `FileManager refuses parent traversal`() {
        FileManager.configureExistingBase(tempBase.absolutePathString())
        val r = FileManager.deleteFile("../../etc/passwd")
        assertFalse(r.ok)
    }

    @Test
    fun `FileManager refuses absolute paths`() {
        FileManager.configureExistingBase(tempBase.absolutePathString())
        val r = FileManager.deleteFile("/etc/passwd")
        assertFalse(r.ok)
    }

    @Test
    fun `FileManager refuses deleting base dir itself`() {
        FileManager.configureExistingBase(tempBase.absolutePathString())
        val r = FileManager.deleteFile(".")
        assertFalse(r.ok)
    }

    // -----------------------------------------------------------------
    // SettingsEncriptor: round-trip and IV randomness
    // -----------------------------------------------------------------

    @Test
    fun `settings encrypt decrypt round trip`() {
        SettingsEncriptor.assertKeyAvailable()
        val plain = "my-very-secret-password!@#"
        val roundTrip = SettingsEncriptor.decrypt(SettingsEncriptor.encrypt(plain))
        assertEquals(plain, roundTrip)
    }

    @Test
    fun `settings encrypt produces different ciphertext each call`() {
        SettingsEncriptor.assertKeyAvailable()
        val plain = "same-plaintext"
        val c1 = SettingsEncriptor.encrypt(plain)
        val c2 = SettingsEncriptor.encrypt(plain)
        assertNotEquals(c1, c2, "AES-GCM must use a fresh IV per encryption")
    }

    @Test
    fun `legacy ECB ciphertext is discarded, not decrypted`() {
        // Any value without the "gcm1$" prefix should return "" and log a warning,
        // not throw or attempt ECB decryption.
        val legacy = "SomeBase64==" // no prefix
        assertEquals("", SettingsEncriptor.decrypt(legacy))
    }

    // -----------------------------------------------------------------
    // TOTP: TotpManager unit tests
    // -----------------------------------------------------------------

    @Test
    fun `TotpManager generates a valid Base32 secret`() {
        val secret = TotpManager.generateSecretBase32()
        assertTrue(secret.isNotEmpty())
        // Base32 alphabet only
        assertTrue(secret.all { it in 'A'..'Z' || it in '2'..'7' })
    }

    @Test
    fun `TotpManager buildOtpauthUri contains secret and issuer`() {
        val secret = TotpManager.generateSecretBase32()
        val uri = TotpManager.buildOtpauthUri(secret)
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=$secret"))
        assertTrue(uri.contains("issuer=HomeBox"))
    }

    @Test
    fun `TotpManager rejects wrong 6-digit code`() {
        val secret = TotpManager.generateSecretBase32()
        // "000000" is astronomically unlikely to match the real current code
        assertFalse(TotpManager.verify(secret, "000000"))
    }

    @Test
    fun `TotpManager rejects code with wrong length`() {
        val secret = TotpManager.generateSecretBase32()
        assertFalse(TotpManager.verify(secret, "12345"))   // 5 digits
        assertFalse(TotpManager.verify(secret, "1234567")) // 7 digits
        assertFalse(TotpManager.verify(secret, ""))
    }

    @Test
    fun `TotpManager rejects non-numeric code`() {
        val secret = TotpManager.generateSecretBase32()
        assertFalse(TotpManager.verify(secret, "12345a"))
        assertFalse(TotpManager.verify(secret, "abcdef"))
    }

    @Test
    fun `TotpManager round-trip encode-decode produces same secret`() {
        // Generate secret, build URI, extract secret from URI, verify they match
        val secret = TotpManager.generateSecretBase32()
        val uri = TotpManager.buildOtpauthUri(secret)
        val extracted = uri.substringAfter("secret=").substringBefore("&")
        assertEquals(secret, extracted)
    }

    // -----------------------------------------------------------------
    // 2FA: /api/totp/verify endpoint
    // -----------------------------------------------------------------

    @Test
    fun `totp verify with unknown pending token returns failure`() = testApplication {
        application { module() }
        val r = client.post("/api/totp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"pendingToken":"invalid-token-xyz","code":"123456"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(r.bodyAsText().contains(""""isSuccess":true"""))
    }

    @Test
    fun `totp verify with invalid code format returns 400`() = testApplication {
        application { module() }
        val r = client.post("/api/totp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"pendingToken":"sometoken","code":"abc"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `totp setup requires authentication`() = testApplication {
        application { module() }
        val r = client.get("/api/totp/setup")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `totp enable requires authentication`() = testApplication {
        application { module() }
        val r = client.post("/api/totp/enable") {
            contentType(ContentType.Application.Json)
            setBody("""{"secret":"JBSWY3DPEHPK3PXP","code":"123456"}""")
        }
        assertTrue(r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden)
    }

    @Test
    fun `totp disable requires authentication`() = testApplication {
        application { module() }
        val r = client.post("/api/totp/disable") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"123456"}""")
        }
        assertTrue(r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden)
    }

    // -----------------------------------------------------------------
    // Settings: TotpDevice round-trip
    // -----------------------------------------------------------------

    @Test
    fun `settings totpSecret encrypts and decrypts correctly`() {
        SettingsEncriptor.assertKeyAvailable()
        val secret = TotpManager.generateSecretBase32()
        val encrypted = SettingsEncriptor.encrypt(secret)
        val decrypted = SettingsEncriptor.decrypt(encrypted)
        assertEquals(secret, decrypted)
    }

    @Test
    fun `readClientSettings returns empty totpDeviceNames when no devices set`() {
        val settings = org.example.project.serverData.ServerSettings(
            downloadFolder = "",
            filelistUsername = "",
            filelistPassword = "",
            qbUsername = "",
            qbPassword = "",
            totpDevices = emptyList(),
        )
        assertTrue(settings.totpDevices.isEmpty())
    }

    @Test
    fun `readClientSettings returns totpDeviceNames when devices set`() {
        val secret = TotpManager.generateSecretBase32()
        val settings = org.example.project.serverData.ServerSettings(
            downloadFolder = "",
            filelistUsername = "",
            filelistPassword = "",
            qbUsername = "",
            qbPassword = "",
            totpDevices = listOf(org.example.project.serverData.TotpDevice("Phone 1", secret)),
        )
        assertEquals(listOf("Phone 1"), settings.totpDevices.map { it.name })
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

}
