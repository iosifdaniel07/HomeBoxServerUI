package org.example.project.searchData

import kotlinx.serialization.Serializable

@Serializable
data class SearchItem(
    val title: String,
    val imageUrl: String,
    val detailLink: String,
    val id: String,
    val categories: String,
    val imageCategory: String?,
    val size: String? = null,
    val uploadedDate: String? = null
)
