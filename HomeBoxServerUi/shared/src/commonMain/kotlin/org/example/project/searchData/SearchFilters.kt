package org.example.project.searchData

import kotlinx.serialization.Serializable

enum class SearchFilters(name: String) {
    CategoryOptions("Category Options"),
    SearchInOptions("Search In Options"),
    SortOptions("Sort Options")
}

@Serializable
data class CategoryOptions(val value: Int, val text: String)

@Serializable
data class SearchInOptions(val value: Int, val text: String)

@Serializable
data class SortOptions(val value: Int, val text: String)

@Serializable
data class SearchFiltersData(
    val isSuccessful: Boolean,
    val categoryOptionsList: List<CategoryOptions>,
    val searchInOptionsList: List<SearchInOptions>,
    val sortOptionsList: List<SortOptions>
)

