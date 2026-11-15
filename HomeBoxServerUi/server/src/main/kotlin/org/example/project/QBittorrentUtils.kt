package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import org.example.project.downloadData.TorrentInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory


object QBittorrentUtils {

    private val log = LoggerFactory.getLogger(QBittorrentUtils::class.java)

    /**
     * Local qBittorrent WebUI URL. Overridable via `HOMEBOX_QBITTORRENT_URL`.
     * Default binds to loopback (127.0.0.1) to make it clear the WebUI must
     * NOT be exposed to the public internet.
     */
    val BASE_URL: String by lazy {
        System.getenv(HOMEBOX_QBITTORRENT_URL)?.takeIf { it.isNotBlank() }
            ?: "http://127.0.0.1:8080"
    }

    val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {

        install(ContentNegotiation) {
            json(json) // Configure to ignore unknown keys if necessary
        }

        install(HttpCookies) {
            // Automatically handles cookies including session IDs
            storage = AcceptAllCookiesStorage()
        }

    }

    suspend fun loginToQbittorrent(
        baseUrl: String = BASE_URL
    ): Boolean {
        val settings = SettingsEncriptor.readSettingsFromFile()
        val username: String = settings.qbUsername
        val password: String = settings.qbPassword
        client.post(baseUrl)
        val response: HttpResponse = client.post("$baseUrl/api/v2/auth/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=$username&password=$password")
            header("Origin", baseUrl)
            header("Referer", baseUrl)
        }
        val responseString = response.bodyAsText()
        return responseString == "Ok."
    }

    suspend fun logout(baseUrl: String = BASE_URL) {
        val response = client.post("$baseUrl/api/v2/auth/logout")
        if (response.status.isSuccess()) {
            log.debug("qbittorrent logout ok")
        } else {
            log.warn("qbittorrent logout failed status={}", response.status)
        }
    }

    // Cache for the probe result. UI polls this on an interval, but we don't
    // want to re-login to qBittorrent on every click: that's one process spawn
    // + one HTTP POST per call. Instead, we cache for 5 seconds.
    @Volatile
    private var probeCacheResult: Boolean = false
    @Volatile
    private var probeCacheAtMs: Long = 0L
    private val probeTtlMs = 5_000L

    suspend fun isQbittorrentRunning(): Boolean {
        val now = System.currentTimeMillis()
        if (now - probeCacheAtMs < probeTtlMs) return probeCacheResult
        val result = try {
            val process = ProcessBuilder("which", "qbittorrent-nox").start()
            val binary = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            val loginResponse = loginToQbittorrent()
            binary != null && loginResponse
        } catch (e: Exception) {
            log.debug("qbittorrent probe failed: {}", e.javaClass.simpleName)
            false
        }
        probeCacheResult = result
        probeCacheAtMs = System.currentTimeMillis()
        return result
    }

    /**
     * Adds a torrent to qBittorrent from raw .torrent bytes.
     *
     * @param client  Ktor HttpClient (must support cookies if you log in elsewhere).
     * @param bytes   Raw .torrent file bytes.
     * @param fileName Name sent as filename in multipart (e.g. "something.torrent").
     * @param category Optional category.
     * @param paused  Add in paused state (default false).
     */
    suspend fun addTorrentFile(
        bytes: ByteArray,
        fileName: String,
        category: String? = null,
        paused: Boolean = false
    ): Boolean {
        // Save inside the operator-configured base dir, same as where the
        // torrent file itself is written. No more hidden $HOME/Downloads path.
        val savePath = FileManager.baseDir().toString()
        loginToQbittorrent()
        val response = client.post("$BASE_URL/api/v2/torrents/add") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "torrents",
                            value = bytes,
                            headers = Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"torrents\"; filename=\"$fileName\""
                                )
                                append(
                                    HttpHeaders.ContentType,
                                    "application/x-bittorrent"
                                )
                            }
                        )
                        append("savepath", savePath)

                        category?.let {
                            append("category", it)
                        }
                        if (paused) {
                            append("paused", "true")
                        }
                    }
                )
            )
        }

        // qBittorrent returns:
        // - 200 for "all other scenarios"
        // - 415 for invalid torrent
        return response.status == HttpStatusCode.OK
    }


    suspend fun getTorrentsList(
        filter: String? = null,      // "all", "downloading", etc.
        category: String? = null,    // "" = fără categorie; null = orice categorie
        tag: String? = null,         // "" = fără tag; null = orice tag
        sort: String? = null,        // ex: "ratio", "name"
        reverse: Boolean? = null,
        limit: Int? = null,
        offset: Int? = null,
        hashes: String? = null       // ex: "hash1|hash2|hash3"
    ): List<TorrentInfo> {

        val response = client.get("$BASE_URL/api/v2/torrents/info") {
            contentType(ContentType.Application.Json)
            filter?.let { parameter("filter", it) }
            category?.let { parameter("category", it) } // Ktor face URL-encoding corect
            tag?.let { parameter("tag", it) }
            sort?.let { parameter("sort", it) }
            reverse?.let { parameter("reverse", it) }
            limit?.let { parameter("limit", it) }
            offset?.let { parameter("offset", it) }
            hashes?.let { parameter("hashes", it) }
        }.bodyAsText()

        return json.decodeFromString(response)
    }

    suspend fun deleteTorrent(
        hash: String,
        deleteFile: Boolean = false
    ): Boolean {
        require(hash.matches(Regex("^[A-Fa-f0-9]{40}\$|^[A-Fa-f0-9]{64}\$"))) {
            "invalid torrent hash"
        }
        val response = client.submitForm(
            url = "$BASE_URL/api/v2/torrents/delete",
            formParameters = Parameters.build {
                append("hashes", hash)
                append("deleteFiles", deleteFile.toString())
            }
        ) {
            header(HttpHeaders.Origin, BASE_URL)
            header(HttpHeaders.Referrer, "$BASE_URL/")
        }
        return response.status == HttpStatusCode.OK
    }
}