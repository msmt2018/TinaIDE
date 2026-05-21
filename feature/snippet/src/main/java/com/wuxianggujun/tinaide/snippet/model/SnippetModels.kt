package com.wuxianggujun.tinaide.snippet.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SnippetListData(
    val snippets: List<SnippetSummary>,
    val pagination: Pagination
)

@Serializable
data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Long,
    @SerialName("total_pages")
    val totalPages: Int
)

@Serializable
data class SnippetSummary(
    val id: String,
    val title: String,
    val description: String? = null,
    val language: String,
    @SerialName("code_preview")
    val codePreview: String,
    val author: AuthorInfo,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    @SerialName("favorite_count")
    val favoriteCount: Long = 0,
    @SerialName("copy_count")
    val copyCount: Long = 0,
    @SerialName("rating_avg")
    val ratingAvg: Double = 0.0,
    @SerialName("rating_count")
    val ratingCount: Int = 0,
    @SerialName("is_featured")
    val isFeatured: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class AuthorInfo(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class SnippetDetail(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("code_content")
    val codeContent: String,
    val language: String,
    @SerialName("file_extension")
    val fileExtension: String? = null,
    val author: AuthorInfo,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    @SerialName("view_count")
    val viewCount: Long = 0,
    @SerialName("copy_count")
    val copyCount: Long = 0,
    @SerialName("favorite_count")
    val favoriteCount: Long = 0,
    @SerialName("rating_avg")
    val ratingAvg: Double = 0.0,
    @SerialName("rating_count")
    val ratingCount: Int = 0,
    @SerialName("is_featured")
    val isFeatured: Boolean = false,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("is_favorited")
    val isFavorited: Boolean = false,
    @SerialName("my_rating")
    val myRating: Int? = null
)
