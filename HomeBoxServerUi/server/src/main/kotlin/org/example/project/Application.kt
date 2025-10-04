package org.example.project

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }

    val filelistClient = FilelistClient()

    routing {
        post("/login") {
            val loginRequest = call.receive<LoginRequest>()
            val (success, homePage) = filelistClient.login(loginRequest.username, loginRequest.password)
            call.respond(LoginResponse(success, homePage))
        }

        get("/") {
            call.respondText("Server is running!", ContentType.Text.Plain)
        }
    }
}