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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.searchData.SearchCompleteItem
import org.example.project.serverData.DeleteItem
import org.example.project.serverData.DeleteResponse
import org.example.project.serverData.DownloadItem

const val desired = "/home/daniel/Desktop/testt"

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


    val filelistClient = FilelistClient()

   // println(desired)
   /* val baseCfg = FileManager.configureExistingBase(desired) //todo: enable
    require(baseCfg.ok) { baseCfg.message }*/


    routing {

        get("/get") {
            call.respondText("Server is running!", ContentType.Text.Plain)
        }

        post("/login") {
            val loginRequest = call.receive<LoginRequest>()

            // First, attempt login
            val success = filelistClient.login(loginRequest.username, loginRequest.password)
            call.respond(HttpStatusCode.OK, LoginResponse(success))
        }

        get("/firstSearch") {
            println("first search")
            val results = filelistClient.firstSearch()
            call.respond(HttpStatusCode.OK, results)
        }

        post("/search") {
            println("search")
            val searchItem = call.receive<SearchCompleteItem>()
            println(searchItem)
            val results = filelistClient.search(searchItem)
            call.respond(HttpStatusCode.OK, results)
        }

        get("/diskSpace") {
            val usage = withContext(Dispatchers.IO) { getAllDiskUsage() }
            call.respond(usage)
        }

        get("/dirData") {
            val usage = withContext(Dispatchers.IO) { FileManager.list() }
            call.respond(usage)
        }

        post("/deleteFile") {
            val fileName = call.receive<DeleteItem>()
            println("delete file: ${fileName}")
            val deleted = withContext(Dispatchers.IO) { FileManager.deleteFile(fileName.item) }
            call.respond(DeleteResponse(deleted.ok))
        }

        get("/qBittorrentRunning") {
            val isInstalled =
                withContext(Dispatchers.IO) { QBittorrentUtils.isQbittorrentRunning() }
            call.respond(isInstalled)
        }

        post("/downloadFile"){
            val item = call.receive<DownloadItem>()
            val response =
                withContext(Dispatchers.IO) { filelistClient.downloadFile(item.itemId)}
            call.respond(response)
        }
    }
}