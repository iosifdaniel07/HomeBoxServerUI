package org.example.project

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import org.example.project.downloadData.TorrentInfo
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchCompleteItem
import org.example.project.searchData.SearchResponse
import org.example.project.serverData.DeleteItem
import org.example.project.serverData.DeleteResponse
import org.example.project.serverData.DirListing
import org.example.project.serverData.DownloadItem
import org.example.project.serverData.DownloadStatus
import org.example.project.serverData.FilesystemUsage
import org.example.project.serverData.ServerSettings
import org.example.project.serverData.ServerSettingsClient


// Shared class to handle API calls in common code
object Client {
    val client = HttpClient {//(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Resolved per-platform (see Platform.kt). Web targets return
    // `${window.location.origin}/api`, so the UI always speaks to the same
    // origin that served it — no hardcoded hostnames, no CORS surprises.
    val host: String = apiBaseUrl()

    /**
     * CSRF token from the current session. Captured at /api/login and /api/me,
     * echoed back via the `X-CSRF-Token` header on every state-changing call.
     * Null = not authenticated yet.
     *
     * Not `@Volatile` because web (JS/Wasm) targets are single-threaded; any
     * JVM-only callers must ensure external synchronization if they race.
     */
    var csrfToken: String? = null
        private set

    private fun HttpRequestBuilder.withCsrf() {
        csrfToken?.let { header("X-CSRF-Token", it) }
    }

    suspend fun login(username: String, password: String): LoginResponse {
        val response: LoginResponse = client.post("${host}/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body()
        if (response.isSuccess) csrfToken = response.csrfToken
        return response
    }

    suspend fun me(): MeResponse {
        val r: MeResponse = client.get("${host}/me").body()
        csrfToken = r.csrfToken
        return r
    }

    suspend fun logout() {
        try {
            client.post("${host}/logout") { withCsrf() }
        } finally {
            csrfToken = null
        }
    }

    suspend fun firstSearch(): FirstSearchResponse {
        val response: FirstSearchResponse =
            client.get("${host}/firstSearch").body()
        return response
    }

    suspend fun search(item: SearchCompleteItem): SearchResponse {
        val response: SearchResponse = client.post("${host}/search") {
            withCsrf()
            contentType(ContentType.Application.Json)
            setBody(item)
        }.body()
        return response
    }

    suspend fun diskSize(): FilesystemUsage {
        val response: FilesystemUsage = client.get("${host}/diskSpace") {
        }.body()
        return response
    }

    suspend fun list(): DirListing {
        val response: DirListing = client.get("${host}/dirData") {
        }.body()
        return response
    }

    suspend fun deleteFile(file: DeleteItem): Boolean {
        val response: DeleteResponse = client.post("${host}/deleteFile") {
            withCsrf()
            contentType(ContentType.Application.Json)
            setBody(file)
        }.body()
        return response.result
    }


    suspend fun qBittorentRunning(): Boolean {
        val response: Boolean = client.get("${host}/qBittorrentRunning") {
        }.body()
        return response
    }

    suspend fun downloadFile(item: DownloadItem): DownloadStatus {
        val response: DownloadStatus = client.post("${host}/downloadFile") {
            withCsrf()
            contentType(ContentType.Application.Json)
            setBody(item)
        }.body()
        return response
    }

    suspend fun getTorrentsStatus(): List<TorrentInfo> {
        val response = client.get("${host}/torrentsStatus") {
        }.body<List<TorrentInfo>>()
        return response
    }

    suspend fun deleteTorrent(hash: String): Boolean {
        val response = client.post("${host}/deleteTorrent") {
            withCsrf()
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(hash)
        }.body<Boolean>()
        return response
    }

    suspend fun getServerSettins(): ServerSettingsClient {
        val resposne =
            client.get("${host}/getServerSettings").body<ServerSettingsClient>()
        return resposne
    }

    suspend fun saveServerSettings(serverSettings: ServerSettingsClient): Boolean {
        val resposne = client.post("${host}/saveServerSettings") {
            withCsrf()
            contentType(ContentType.Application.Json)
            setBody(serverSettings)
        }.body<Boolean>()
        return resposne
    }

    // Clean up resources when done
    fun close() {
        client.close()
    }
}
