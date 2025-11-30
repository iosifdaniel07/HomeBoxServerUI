package org.example.project

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchCompleteItem
import org.example.project.searchData.SearchFiltersData
import org.example.project.searchData.SearchResponse
import org.example.project.serverData.DownloadStatus
import org.jsoup.Jsoup
import java.io.File

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
    var cachedValidator: String? = null
    var cookiesHeader: String? = null

    val headers: HttpRequestBuilder.() -> Unit = {
        header(
            HttpHeaders.Accept,
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        header("Referer", "https://filelist.io/index.php")
        header("priority", "u=0, i")
        header(
            "sec-ch-ua",
            "\"Brave\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\""
        )
        header("sec-ch-ua-mobile", "?0")
        header("sec-ch-ua-platform", "\"Linux\"")
        header("sec-fetch-dest", "document")
        header("sec-fetch-mode", "navigate")
        header("sec-fetch-site", "same-origin")
        header("sec-fetch-user", "?1")
        header("sec-gpc", "1")
        header("upgrade-insecure-requests", "1")
        header(
            HttpHeaders.UserAgent,
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        )
        //header("Cookie", cookiesHeader) //todo...check the flow here...
        header(
            "Cookie",
            "PHPSESSID=apmbbir6uuvbhpol2lpiliteu8; uid=1411920; pass=d8db42735ab25cb809ab5b9ef6b07b11"
        )
    }

    /**
     * Login to filelist.io
     * @param username The username
     * @param password The password
     * @return true if login was successful, false otherwise
     */
    suspend fun login(username: String, password: String): Boolean {
        println("Username: ${username}")
        return try {
            val validatorValue = getValidator(client)
            cachedValidator = validatorValue
            if (validatorValue == null) return false

            val response = client.post("https://filelist.io/takelogin.php") {
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                )
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.CacheControl, "max-age=0")
                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                header(HttpHeaders.Origin, "https://filelist.io")
                header(HttpHeaders.Referrer, "https://filelist.io/login.php?returnto=%2F")
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
                )
                header("Upgrade-Insecure-Requests", "1")

                setBody(
                    Parameters.build {
                        append("validator", validatorValue)
                        append("username", username)
                        append("password", password)
                        append("code", "")  // Verify if 'code' needs a value or should remain empty
                        append("unlock", "1")
                        append("returnto", "%2F")
                    }.formUrlEncode()
                )
            }

            println("Status: ${response.status}")
            println("Headers: ${response.headers}")
            println("Body: ${response.bodyAsText()}")

            cookiesHeader = extractCookies(client)
            println("heree cookies: $cookiesHeader")

            if (response.bodyAsText().contains("Invalid login attempt!!")) {
                println("Login failed: Invalid credentials")
                return false
            }

            println("Here status: ${response.status}")
            response.status == HttpStatusCode.Found
        } catch (e: Exception) {
            println("Login error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadFile(itemId: String): DownloadStatus {
        println("donwload item: $itemId")
       val response = client.post("https://filelist.io/download.php?id=${itemId}") {
            headers()
        }

        if (!response.status.isSuccess()) {
            return DownloadStatus(false, error = "HTTP ${response.status}")
        }

        val bytes: ByteArray = response.body()
        val header = response.headers[HttpHeaders.ContentDisposition]
        val fileNameFromHeader = header
            ?.substringAfter("filename=\"", missingDelimiterValue = "")
            ?.substringBefore("\"", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }

        val fileName = fileNameFromHeader
        if(fileName == null){
            return DownloadStatus(false, error = "No filename in header")
        }
        val home = System.getProperty("home")
        val downloadsDir = File(home, "TorrentsDownloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val outFile = File(downloadsDir, fileName)
        outFile.writeBytes(bytes)

        println("Saved to: ${outFile.absolutePath}")

        return DownloadStatus(true)
    }

    suspend fun firstSearch(): FirstSearchResponse {
        try {
            val response = client.get("https://filelist.io/browse.php") {
                headers()
            }
            println("status" + response.status)
            println("headers:..." + response.headers)
            val document = Jsoup.parse(response.bodyAsText())
            val searchItems = extractTorrentClasses(document)
            return FirstSearchResponse(extractSelectOptions(document), searchItems)
        } catch (e: Exception) {

        }
        return FirstSearchResponse(
            SearchFiltersData(
                false,
                listOf(),
                listOf(),
                listOf(),
                null,
                null,
                null,
                Pair(1, 1)
            ), listOf()
        )
    }

    suspend fun search(searchItem: SearchCompleteItem): SearchResponse {
        try {
            val response = client.get(createSearchUrl(searchItem)) {
                headers()
            }
            println("status" + response.status)
            println("headers:..." + response.headers)
            val document = Jsoup.parse(response.bodyAsText())
            val searchItems = extractTorrentClasses(document)
            val pager = extractPageNumbers(document)
            println("pager: $pager")
            return SearchResponse(true, searchItems, pager)
        } catch (e: Exception) {
            return SearchResponse(false, listOf(), Pair(1, 1))
        }
    }

    private fun createSearchUrl(searchItem: SearchCompleteItem): String {
        val baseUrl = "https://filelist.io/browse.php"
        if (searchItem.selectedSearchIn != null || searchItem.selectedCategory != null || searchItem.selectedSort != null || searchItem.searchTerm != null || searchItem.pageNumber != null) {
            val urlBuilder = StringBuilder(baseUrl)
            urlBuilder.append("?")
            var previousParamAdded = false

            searchItem.searchTerm?.let {
                val searchTermEdited = it.replace(" ", "+")
                urlBuilder.append("search=$searchTermEdited")
                previousParamAdded = true
            }

            searchItem.selectedCategory?.let {
                if (previousParamAdded) {
                    urlBuilder.append("&")
                }
                urlBuilder.append("cat=${it}")
                previousParamAdded = true
            }

            searchItem.selectedSearchIn?.let {
                if (previousParamAdded) {
                    urlBuilder.append("&")
                }
                urlBuilder.append("searchin=${it}")
                previousParamAdded = true
            }

            searchItem.selectedSort?.let {
                if (previousParamAdded) {
                    urlBuilder.append("&")
                }
                urlBuilder.append("sort=${it}")
                previousParamAdded = true
            }
            searchItem.pageNumber?.let {
                if (previousParamAdded) {
                    urlBuilder.append("&")
                }
                urlBuilder.append("page=$it")
            }

            return urlBuilder.toString()
        } else {
            return baseUrl
        }
    }

    /**
     * Close the HTTP client and release resources
     */
    fun close() {
        client.close()
    }
}