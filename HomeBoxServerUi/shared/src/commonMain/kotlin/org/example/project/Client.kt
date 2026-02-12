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


// Shared class to handle API calls in common code
object Client {
    val client = HttpClient {//(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    val isProd = true //todo...
    val port = 8085
    val host = if (isProd) "https://homestreambox.go.ro/api" else "http://localhost${port}" //todo....
    //Caddy should remove /api prefix, but in dev we need it to avoid conflicts with the server running on the same machine.
    suspend fun login(username: String, password: String): LoginResponse {
        val response: LoginResponse = client.post("${host}/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body()
        return response
    }

    suspend fun firstSearch(): FirstSearchResponse {
        val response: FirstSearchResponse =
            client.get("${host}/firstSearch").body()
        return response
    }

    suspend fun search(item: SearchCompleteItem): SearchResponse {
        val response: SearchResponse = client.post("${host}/search") {
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
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(hash)
        }.body<Boolean>()
        return response
    }

    suspend fun getServerSettins(): ServerSettings {
        val resposne =
            client.get("${host}/getServerSettings").body<ServerSettings>()
        return resposne
    }

    suspend fun saveServerSettings(serverSettings: ServerSettings): Boolean {
        val resposne = client.post("${host}/saveServerSettings") {
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
