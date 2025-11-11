package org.example.project

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchCompleteItem
import org.example.project.searchData.SearchResponse
import org.example.project.serverData.DirListing
import org.example.project.serverData.FilesystemUsage


// Shared class to handle API calls in common code
object Client {
    val client = HttpClient {//(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun login(username: String, password: String): LoginResponse {
        val response: LoginResponse = client.post("http://localhost:8085/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body()
        return response
    }

    suspend fun firstSearch(): FirstSearchResponse {
        val response: FirstSearchResponse =
            client.get("http://localhost:8085/firstSearch").body()
        return response
    }

    suspend fun search(item: SearchCompleteItem): SearchResponse {
        val response: SearchResponse = client.post("http://localhost:8085/search") {
            contentType(ContentType.Application.Json)
            setBody(item)
        }.body()
        return response
    }

    suspend fun diskSize(): FilesystemUsage {
        val response: FilesystemUsage = client.get("http://localhost:8085/diskSpace") {
        }.body()
        return response
    }

    suspend fun list(): DirListing {
        val response: DirListing = client.get("http://localhost:8085/dirData") {
        }.body()
        return response
    }

    suspend fun deleteFile(fileName: String): Boolean {
        val response: Boolean = client.post("http://localhost:8085/deleteFile") {
            contentType(ContentType.Application.Json)
            setBody(fileName)
        }.body()
        return response
    }

    // Clean up resources when done
    fun close() {
        client.close()
    }
}
