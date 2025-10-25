package org.example.project

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String
)

@Serializable
data class SearchResponse(
    val queryParam: String
)