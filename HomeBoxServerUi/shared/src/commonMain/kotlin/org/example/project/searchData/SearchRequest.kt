package org.example.project.searchData

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String
)

@Serializable
data class SearchResponse(
    val searchFiltersData: SearchFiltersData
)

@Serializable
data class FirstSearchResponse(
    val searchFiltersData: SearchFiltersData,
    val searchItems: List<SearchItem>
)