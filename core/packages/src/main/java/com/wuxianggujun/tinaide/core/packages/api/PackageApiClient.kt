package com.wuxianggujun.tinaide.core.packages.api

import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.ApiEnvelopeParseResult
import com.wuxianggujun.tinaide.core.network.ApiEnvelopeParser
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.OkHttpClientProvider
import com.wuxianggujun.tinaide.core.packages.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException


class PackageApiClient private constructor(
    private val baseUrl: String,
    private val client: OkHttpClient
) {
    private val json = JsonSerializer.default
    companion object {
        private const val TAG = "PackageApiClient"

        @Volatile
        private var instance: PackageApiClient? = null

        fun getInstance(baseUrl: String): PackageApiClient {
            return instance ?: synchronized(this) {
                instance ?: createInstance(baseUrl).also { instance = it }
            }
        }

        private fun createInstance(baseUrl: String): PackageApiClient {
            return PackageApiClient(
                baseUrl = baseUrl.trimEnd('/'),
                client = OkHttpClientProvider.default
            )
        }

        fun resetInstance() {
            instance = null
        }
    }

    suspend fun getPackages(
        page: Int = 1,
        pageSize: Int = 50,
        category: String? = null,
        platform: String? = null,
        search: String? = null
    ): ApiResult<PackageListResponse> {
        val params = mutableListOf<String>()
        params.add("page=$page")
        params.add("page_size=$pageSize")
        category?.let { params.add("category=$it") }
        platform?.let { params.add("platform=$it") }
        search?.let { params.add("search=$it") }

        val queryString = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return get("/api/packages$queryString")
    }

    suspend fun getCategories(): ApiResult<List<PackageCategory>> {
        return get("/api/packages/categories")
    }

    suspend fun getPackageDetail(packageId: String): ApiResult<GUIPackage> {
        return get("/api/packages/$packageId")
    }

    suspend fun getPackageVersions(packageId: String): ApiResult<PackageVersionsResponse> {
        return get("/api/packages/$packageId/versions")
    }

    suspend fun getDownloadInfo(packageId: String, versionId: Int): ApiResult<DownloadInfo> {
        return get("/api/packages/$packageId/versions/$versionId/download")
    }

    private suspend inline fun <reified T> get(path: String): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .get()
                .build()

            val response = client.newCall(request).execute()
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
        val responseBody = response.body?.string()
        if (responseBody.isNullOrEmpty()) {
            return ApiResult.Error(response.code, Strings.error_response_empty.str())
        }

        // PackageListResponse 需要自定义解析（服务端字段名不统一）
        if (T::class == PackageListResponse::class) {
            return parsePackageListResult(responseBody, response.code) as ApiResult<T>
        }

        return ApiEnvelopeParser.parseToApiResult<T>(responseBody, response.code, TAG)
    }

    private fun parsePackageListResult(body: String, httpCode: Int): ApiResult<PackageListResponse> {
        return when (val parsed = ApiEnvelopeParser.parse<JsonElement>(body)) {
            is ApiEnvelopeParseResult.Success -> {
                ApiResult.Success(parsePackageListResponse(parsed.data))
            }
            is ApiEnvelopeParseResult.ApiError -> ApiResult.Error(
                httpCode,
                ApiEnvelopeParser.formatApiError(parsed.apiCode, parsed.message)
            )
            is ApiEnvelopeParseResult.InvalidFormat -> {
                Timber.tag(TAG).e("Invalid API envelope (http=$httpCode): ${parsed.reason}")
                ApiResult.Error(
                    if (httpCode in 200..299) -1 else httpCode,
                    Strings.error_response_parse_failed.str()
                )
            }
        }
    }

    private fun parsePackageListResponse(data: JsonElement): PackageListResponse {
        val obj = data.jsonObject

        val packagesElement = obj["packages"] ?: obj["items"]
        val packages: List<GUIPackage> = when {
            packagesElement == null -> emptyList()
            packagesElement is JsonObject -> emptyList()
            else -> {
                try {
                    json.decodeFromJsonElement<List<GUIPackage>>(packagesElement)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        val total = obj["total"]?.jsonPrimitive?.int ?: 0
        val page = obj["page"]?.jsonPrimitive?.int ?: 1
        val pageSize = obj["page_size"]?.jsonPrimitive?.int ?: 50

        return PackageListResponse(
            packages = packages,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

}

@Serializable
data class PackageListResponse(
    val packages: List<GUIPackage>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int
)

@Serializable
data class PackageVersionsResponse(
    val linux: List<PackageVersion>? = null,
    val android: List<PackageVersion>? = null
)
