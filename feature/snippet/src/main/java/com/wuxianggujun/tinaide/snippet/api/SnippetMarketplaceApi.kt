package com.wuxianggujun.tinaide.snippet.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.OkHttpClientProvider
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import com.wuxianggujun.tinaide.core.network.ApiEnvelopeParser
import com.wuxianggujun.tinaide.core.network.server.TinaServerHttpClientFactory
import com.wuxianggujun.tinaide.snippet.model.SnippetDetail
import com.wuxianggujun.tinaide.snippet.model.SnippetListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import android.content.Context
import java.io.IOException

class SnippetMarketplaceApi private constructor(
    private val baseUrl: String,
    private val client: OkHttpClient
) {
    private val json = JsonSerializer.default
    companion object {
        private const val TAG = "SnippetMarketplaceApi"

        fun create(context: Context, baseUrl: String): SnippetMarketplaceApi {
            val anonymousClient = TinaServerHttpClientFactory.anonymous(
                baseClient = OkHttpClientProvider.default
            )
            return SnippetMarketplaceApi(
                baseUrl = baseUrl.trimEnd('/'),
                client = anonymousClient
            )
        }
    }

    suspend fun listSnippets(
        page: Int = 1,
        limit: Int = 20,
        search: String? = null,
        language: String? = null,
        category: String? = null,
        sort: String? = null,
        authorId: String? = null,
        includeDrafts: Boolean? = null
    ): ApiResult<SnippetListData> {
        val params = mutableListOf<String>()
        params.add("page=$page")
        params.add("limit=$limit")
        search?.takeIf { it.isNotBlank() }?.let { params.add("search=$it") }
        language?.takeIf { it.isNotBlank() }?.let { params.add("language=$it") }
        category?.takeIf { it.isNotBlank() }?.let { params.add("category=$it") }
        sort?.takeIf { it.isNotBlank() }?.let { params.add("sort=$it") }
        authorId?.takeIf { it.isNotBlank() }?.let { params.add("author_id=$it") }
        includeDrafts?.let { params.add("include_drafts=$it") }
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return get("/api/snippets$query")
    }

    suspend fun getSnippetDetail(id: String): ApiResult<SnippetDetail> {
        return get("/api/snippets/$id")
    }

    suspend fun recordCopy(id: String): ApiResult<CopyResponse> {
        return postEmpty("/api/snippets/$id/copy")
    }

    suspend fun favorite(id: String): ApiResult<FavoriteResponse> {
        return postEmpty("/api/snippets/$id/favorite")
    }

    suspend fun unfavorite(id: String): ApiResult<FavoriteResponse> {
        return delete("/api/snippets/$id/favorite")
    }

    suspend fun rate(id: String, rating: Int): ApiResult<RateResponse> {
        return post("/api/snippets/$id/rate", RateRequest(rating = rating))
    }

    suspend fun createSnippet(body: CreateSnippetRequest): ApiResult<SnippetDetail> {
        return post("/api/snippets", body)
    }

    private suspend inline fun <reified T> get(
        path: String,
        accessToken: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .get()

            accessToken?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            parseResponse(response)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "GET $path network error")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "GET $path unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    private inline fun <reified T> parseResponse(response: okhttp3.Response): ApiResult<T> {
        val bodyString = response.body?.string()
        if (bodyString.isNullOrBlank()) {
            return ApiResult.Error(
                if (response.isSuccessful) -1 else response.code,
                Strings.error_response_empty.str()
            )
        }

        return ApiEnvelopeParser.parseToApiResult<T>(bodyString, response.code, TAG)
    }

    /**
     * 无请求体的 POST 请求（避免 Any 类型序列化问题）
     */
    private suspend inline fun <reified T> postEmpty(
        path: String
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "POST $path network error")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "POST $path unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    /**
     * 带请求体的 POST 请求，使用 reified 类型参数确保编译期序列化
     */
    private suspend inline fun <reified T, reified B : Any> post(
        path: String,
        body: B
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(json.encodeToString<B>(body).toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "POST $path network error")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "POST $path unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    private suspend inline fun <reified T> delete(path: String): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .delete()
                    .build()

                val response = client.newCall(request).execute()
                parseResponse(response)
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "DELETE $path network error")
                ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "DELETE $path unknown error")
                ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
            }
        }
}

@Serializable
data class RateRequest(
    val rating: Int
)

@Serializable
data class FavoriteResponse(
    @SerialName("is_favorited")
    val isFavorited: Boolean,
    @SerialName("favorite_count")
    val favoriteCount: Long
)

@Serializable
data class RateResponse(
    @SerialName("my_rating")
    val myRating: Int,
    @SerialName("rating_avg")
    val ratingAvg: Double,
    @SerialName("rating_count")
    val ratingCount: Int
)

@Serializable
data class CopyResponse(
    @SerialName("copy_count")
    val copyCount: Long
)

@Serializable
data class CreateSnippetRequest(
    val title: String,
    val description: String? = null,
    @SerialName("code_content")
    val codeContent: String,
    val language: String,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("is_draft")
    val isDraft: Boolean = false
)
