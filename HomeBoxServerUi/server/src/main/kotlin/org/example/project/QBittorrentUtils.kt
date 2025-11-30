package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.BufferedReader
import java.io.InputStreamReader

object QBittorrentUtils {

    const val BASE_URL = "http://localhost:8080"

    private val client = HttpClient {
        install(HttpCookies) {
            // Automatically handles cookies including session IDs
            storage = AcceptAllCookiesStorage()
        }

        // Optional: follow redirects automatically
        // followRedirects = true
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
            header("Accept", "/")
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

    suspend fun getActiveTorrents(baseUrl: String = BASE_URL) { //add filter etccc - documentation...
        val response: HttpResponse = client.get("$baseUrl/api/v2/torrents/info") {
            header("Accept", "/")
        }
        val responseString = response.bodyAsText()
        println("Active Torrents: $responseString")
    }

    suspend fun isQbittorrentRunning(): Boolean { //instalati qbittorent.nox-service
        try {
            val process = ProcessBuilder("which", "qbittorrent-nox").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            println("reader ${reader.readLine()}")
            val loginResponse = loginToQbittorrent()
            return reader.readLine() != null && loginResponse
        } catch (e: Exception) {
            return false
        }
    }
}