package org.example.project.searchData

import kotlinx.serialization.Serializable

@Serializable
data class SearchCompleteItem(
    val pageNumber: Int?,
    val selectedSort: Int?,
    val selectedSearchIn: Int?,
    val selectedCategory: Int?,
    val searchTerm: String?
)