package org.example.project

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.example.project.searchData.CategoryOptions
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchFiltersData
import org.example.project.searchData.SearchInOptions
import org.example.project.searchData.SortOptions
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

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

    // PHPSESSID=apmbbir6uuvbhpol2lpiliteu8; uid=1411920; pass=d8db42735ab25cb809ab5b9ef6b07b11
    suspend fun extractCookies(): String {
        val cookies = client.cookies("https://filelist.io/takelogin.php")
        println("cookies: $cookies")
        val PHPSESSID = cookies.find { it.name == "PHPSESSID" }?.value
        val uid = cookies.find { it.name == "uid" }?.value
        val pass = cookies.find { it.name == "pass" }?.value
        return "PHPSESSID=$PHPSESSID; uid=$uid; pass=$pass"
    }

    suspend fun getValidator(): String? {
        return try {
            val response = client.get("https://filelist.io/") {
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                )
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(
                    "Referer",
                    "https://search.brave.com/"
                )  // Directly using string key for Referer
                header("priority", "u=0, i")
                header(
                    "sec-ch-ua",
                    "\"Brave\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\""
                )
                header("sec-ch-ua-mobile", "?0")
                header("sec-ch-ua-platform", "\"Linux\"")
                header("sec-fetch-dest", "document")
                header("sec-fetch-mode", "navigate")
                header("sec-fetch-site", "cross-site")
                header("sec-fetch-user", "?1")
                header("sec-gpc", "1")
                header("upgrade-insecure-requests", "1")
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                )
            }

            // Parse the HTML response to extract the validator field
            val html = response.bodyAsText()
            val document = Jsoup.parse(html)

            // Extract the validator field (assuming it is present in a hidden input field with the name 'validator')
            val validator = document.select("input[name=validator]").attr("value")

            // Log and return the extracted validator
            println("Validator: $validator")

            cachedValidator = validator.takeIf { it.isNotEmpty() }
            validator.takeIf { it.isNotEmpty() }

        } catch (e: Exception) {
            println("Error fetching the page or extracting validator: ${e.message}")
            null
        }
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
            val validatorValue = getValidator()
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

            cookiesHeader = extractCookies()
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

    fun extractSelectOptions(document: Document): SearchFiltersData {

        // Extract the 'cat' select options
        val catSelect: Element? = document.select("select[name=cat]").first()
        val catOptions: Elements? = catSelect?.select("option")

        val categories = mutableListOf<CategoryOptions>()
        catOptions?.let {
            for (option in catOptions) {
                val value = option.attr("value")
                val text = option.text()
                categories.add(CategoryOptions(value.toInt(), text))
            }
        }

        // Extract the 'searchin' select options
        val searchinSelect: Element? = document.select("select[name=searchin]").first()
        val searchinOptions: Elements? = searchinSelect?.select("option")

        val searchIn = mutableListOf<SearchInOptions>()
        searchinOptions?.let {
            for (option in searchinOptions) {
                val value = option.attr("value")
                val text = option.text()
                searchIn.add(SearchInOptions(value.toInt(), text))
            }
        }

        // Extract the 'sort' select options
        val sortSelect: Element? = document.select("select[name=sort]").first()
        val sortOptions: Elements? = sortSelect?.select("option")

        val sort = mutableListOf<SortOptions>()
        sortOptions?.let {
            for (option in sortOptions) {
                val value = option.attr("value")
                val text = option.text()
                sort.add(SortOptions(value.toInt(), text))
            }
        }
        return SearchFiltersData(true, categories, searchIn, sort)
    }

    suspend fun firstSearch(): FirstSearchResponse {
        try {
            val response = client.get("https://filelist.io/browse.php") {
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
                //header("Cookie", cookiesHeader)
                header(
                    "Cookie",
                    "PHPSESSID=apmbbir6uuvbhpol2lpiliteu8; uid=1411920; pass=d8db42735ab25cb809ab5b9ef6b07b11"
                )
            }
            println("status" + response.status)
            println("headers:..." + response.headers)
            val document = Jsoup.parse(response.bodyAsText())
            return FirstSearchResponse(extractSelectOptions(document), listOf())
        } catch (e: Exception) {

        }
        return FirstSearchResponse(SearchFiltersData(false, listOf(), listOf(), listOf()), listOf())
    }

    /**
     * Close the HTTP client and release resources
     */
    fun close() {
        client.close()
    }
}