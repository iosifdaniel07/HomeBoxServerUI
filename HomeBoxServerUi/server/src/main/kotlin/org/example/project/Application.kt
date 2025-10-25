package org.example.project

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchRequest
import org.example.project.searchData.SearchResponse

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
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
    }


    val client = FilelistClient()

    routing {

        get("/get") {
            call.respondText("Server is running!", ContentType.Text.Plain)
        }

        post("/login") {
            val loginRequest = call.receive<LoginRequest>()

            // First, attempt login
            val success = client.login(loginRequest.username, loginRequest.password)
            call.respond(HttpStatusCode.OK, LoginResponse(success))
        }

       /* post("/search") {
            val query = call.receive<SearchRequest>().query//todo...
            val results = client.firstSearch()
            println("search here : ${query}")
            call.respond(HttpStatusCode.OK, SearchResponse(results))
        }*/

        get("/firstSearch") {
            val results = client.firstSearch()
            call.respond(HttpStatusCode.OK, results)
        }
    }
}