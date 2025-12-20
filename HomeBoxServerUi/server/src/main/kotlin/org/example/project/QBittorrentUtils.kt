package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
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
import java.io.File
import java.io.InputStreamReader
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json


object QBittorrentUtils {

    const val BASE_URL = "http://localhost:8080"

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
        baseUrl: String = BASE_URL,
        username: String = "admin",
        password: String = "admin07"
    ): Boolean {
        client.post(baseUrl)
        val response: HttpResponse = client.post("$baseUrl/api/v2/auth/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=$username&password=$password")
            header("Origin", baseUrl)
            header("Referer", baseUrl)
        }
        val responseString = response.bodyAsText()
        println("is running? $responseString")
        return responseString == "Ok."
    }

    suspend fun logout(baseUrl: String = BASE_URL) {
        println("logout ....")
        val response = client.post("$baseUrl/api/v2/auth/logout")
        if (response.status.isSuccess()) {
            println("Logout successful.")
        } else {
            println("Logout failed with status: ${response.status}")
        }
    }

    suspend fun isQbittorrentRunning(): Boolean { //instalati qbittorent.nox-service
        try {
            val process = ProcessBuilder("which", "qbittorrent-nox").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            println("reader ${reader}")
            val loginResponse = loginToQbittorrent()
            return reader != null && loginResponse
        } catch (e: Exception) {
            return false
        }
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
        val home = System.getProperty("user.home")
        val downloadsDir = File(home, "Downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        loginToQbittorrent()
        val response = client.post("$BASE_URL/api/v2/torrents/add") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // The important part: the "torrents" field with file bytes
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
                        append("savepath", downloadsDir.absolutePath)

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

        println(response)

        return json.decodeFromString(response)
    }

    suspend fun deleteTorrent(
        hash: String,
        deleteFile: Boolean = false
    ): Boolean {

        val response = client.post("$BASE_URL/api/v2/torrents/delete") {

            // REQUIRED
            header(HttpHeaders.Origin, BASE_URL)
            header(HttpHeaders.Referrer, "$BASE_URL/")

            contentType(ContentType.Application.FormUrlEncoded)

            setBody(
                "hashes=$hash&deleteFiles=$deleteFile"
            )
        }

        println(response.status)
        return response.status == HttpStatusCode.OK
    }
}