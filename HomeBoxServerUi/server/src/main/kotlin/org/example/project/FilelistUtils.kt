package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import org.example.project.searchData.CategoryOptions
import org.example.project.searchData.SearchFiltersData
import org.example.project.searchData.SearchInOptions
import org.example.project.searchData.SearchItem
import org.example.project.searchData.SortOptions
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements


// PHPSESSID=apmbbir6uuvbhpol2lpiliteu8; uid=1411920; pass=d8db42735ab25cb809ab5b9ef6b07b11
suspend fun extractCookies(client: HttpClient): String {
    val cookies = client.cookies("https://filelist.io/takelogin.php")
    println("cookies: $cookies")
    val PHPSESSID = cookies.find { it.name == "PHPSESSID" }?.value
    val uid = cookies.find { it.name == "uid" }?.value
    val pass = cookies.find { it.name == "pass" }?.value
    return "PHPSESSID=$PHPSESSID; uid=$uid; pass=$pass"
}

suspend fun getValidator(client: HttpClient): String? {
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

        validator.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        println("Error fetching the page or extracting validator: ${e.message}")
        null
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

fun extractTorrentClasses(document: Document): List<SearchItem> {

    val searchItemList = mutableListOf<SearchItem>()

    // Extract the class='torrentrow'
    val torrentRows: Elements? = document.select(".torrentrow")
    // Iterate over each 'torrentrow' and extract the 'torrenttable' div inside it
    torrentRows?.let {
        for (torrentRow in torrentRows) {
            // Find the div with class 'torrenttable' inside the current 'torrentrow'
            val torrentTableDivLeft: Element? =
                torrentRow.select("div.torrenttable[align='left']").first()
            val imageUrl = torrentTableDivLeft?.select("span[data-toggle='tooltip']")?.attr("title")
                ?.substringAfter("src='")?.substringBefore("'")
            val detailLink = torrentTableDivLeft?.select("a")?.attr("href")
            val torrentTitle = torrentTableDivLeft?.select("a")?.attr("title")
            val id = getIdFromDetailLink(detailLink ?: "")

            if (torrentTitle != null && imageUrl != null && detailLink != null && id != null)
                searchItemList.add(SearchItem(torrentTitle, imageUrl, detailLink, id))
        }
    }
    return searchItemList
}

fun getIdFromDetailLink(link: String): String? {
    val regex = "id=(\\d+)".toRegex()
    val matchResult = regex.find(link)
    val id = matchResult?.groups?.get(1)?.value
    return id
}