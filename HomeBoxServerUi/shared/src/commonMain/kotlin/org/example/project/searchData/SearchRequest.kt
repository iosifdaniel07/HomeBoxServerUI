package org.example.project.searchData

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String
)

@Serializable
data class SearchResponse(
    val isSuccessful: Boolean,
    val searchItems: List<SearchItem>,
    val firstLastPage: Pair<Int, Int>
)

@Serializable
data class FirstSearchResponse(
    val searchFiltersData: SearchFiltersData,
    val searchItems: List<SearchItem>
)