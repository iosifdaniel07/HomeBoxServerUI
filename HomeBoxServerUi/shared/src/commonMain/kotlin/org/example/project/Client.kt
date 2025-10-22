package org.example.project

import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.*

// Shared class to handle API calls in common code
class Client {
    val client = HttpClient{//(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun login(username: String, password: String): LoginResponse {
        client.post("http://localhost:8085/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return LoginResponse(success = false, homePage = null)
    }


    // Clean up resources when done
    fun close() {
        client.close()
    }

    suspend fun getServerStatus(): String {
        try {
            // Make the GET request to the /get endpoint
            val response: HttpResponse = client.get("http://localhost:8085/hello") {
                contentType(ContentType.Text.Plain) // Expect plain text response
            }
            println("Server response: $response")
            return response.toString()
        } catch (e: Exception) {
            println("Error: ${e.message}")
            return e.message.toString()
        } finally {
            // Always close the client
            client.close()
        }
    }

}
