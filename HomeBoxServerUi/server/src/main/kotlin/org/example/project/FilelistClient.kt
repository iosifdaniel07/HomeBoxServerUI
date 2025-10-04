package org.example.project

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * HTTP client for filelist.io login
 */
class FilelistClient {
    private val client = HttpClient {
        install(HttpCookies) {
            // Automatically handles cookies including session IDs
            storage = AcceptAllCookiesStorage()
        }

        // Optional: follow redirects automatically
        followRedirects = true
    }

    /**
     * Login to filelist.io
     * @param username The username
     * @param password The password
     * @return true if login was successful, false otherwise
     */
    suspend fun login(username: String, password: String): Boolean {
        return try {
            val response = client.post("https://filelist.io/takelogin.php") {
                // Note: No need to set method = HttpMethod.Post (redundant with client.post())
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.CacheControl, "max-age=0")
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                header(HttpHeaders.Origin, "https://filelist.io")
                header(HttpHeaders.Referrer, "https://filelist.io/login.php?returnto=%2F")
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                header("Upgrade-Insecure-Requests", "1")

                // Note: Browser-controlled security headers (Sec-Fetch-*) removed
                // Note: Hardcoded PHPSESSID cookie removed (HttpCookies plugin handles this)

                setBody(
                    Parameters.build {
                        append("username", username)
                        append("password", password)
                        append("code", "unlock")
                        append("unlock", "1")
                        append("returnto", "%2F")
                    }.formUrlEncode()
                )
            }

            println("Status: ${response.status}")
            println("Headers: ${response.headers}")
            println("Body: ${response.bodyAsText()}")

            // Check if login was successful (OK or redirect)
            response.status.isSuccess() || response.status == HttpStatusCode.Found
        } catch (e: Exception) {
            println("Login error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Get the home page content (requires prior login)
     * @return The HTML content or null on error
     */
    suspend fun getHomePage(): String? {
        return try {
            // Cookies are maintained automatically by HttpCookies plugin
            val response = client.get("https://filelist.io/")
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else null
        } catch (e: Exception) {
            println("Error fetching home page: ${e.message}")
            null
        }
    }

    /**
     * Close the HTTP client and release resources
     */
    fun close() {
        client.close()
    }
}