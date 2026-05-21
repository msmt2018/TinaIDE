package com.wuxianggujun.tinaide.plugin.marketplace

import android.content.Context
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.OkHttpClientProvider
import com.wuxianggujun.tinaide.core.network.ApiEnvelopeParser
import com.wuxianggujun.tinaide.core.network.server.TinaServerConfig
import com.wuxianggujun.tinaide.core.network.server.TinaServerHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class PluginMarketplaceApi private constructor(
    private val baseUrl: String,
    private val client: OkHttpClient
) {
    private val json = JsonSerializer.default
    companion object {
        private const val TAG = "PluginMarketplaceApi"

        fun create(context: Context): PluginMarketplaceApi {
            val httpClient = TinaServerHttpClientFactory.anonymous(
                baseClient = OkHttpClientProvider.default
            )
            return PluginMarketplaceApi(
                baseUrl = TinaServerConfig.getBaseUrl().trimEnd('/'),
                client = httpClient
            )
        }
    }

    suspend fun listPlugins(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null,
        sort: String? = null
    ): ApiResult<PluginListData> {
        val params = mutableListOf<String>()
        params.add("page=$page")
        params.add("limit=$limit")
        category?.let { params.add("category=$it") }
        search?.let { params.add("search=$it") }
        sort?.let { params.add("sort=$it") }
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return get("/api/plugins$query")
    }

    suspend fun getPluginDetail(pluginId: String): ApiResult<PluginDetail> {
        return get("/api/plugins/$pluginId")
    }

    suspend fun ratePlugin(pluginId: String, rating: Int): ApiResult<RatePluginResponse> {
        return post("/api/plugins/$pluginId/rate", RatePluginRequest(rating = rating))
    }

    suspend fun submitPluginComment(pluginId: String, content: String): ApiResult<PluginComment> {
        return post("/api/plugins/$pluginId/comments", CreatePluginCommentRequest(content = content))
    }

    suspend fun reportPluginComment(
        pluginId: String,
        commentId: String,
        reason: String,
        details: String?
    ): ApiResult<ReportPluginCommentResponse> {
        return post(
            "/api/plugins/$pluginId/comments/$commentId/report",
            ReportPluginCommentRequest(reason = reason, details = details)
        )
    }

    suspend fun checkUpdates(
        plugins: List<CheckUpdateItem>
    ): ApiResult<CheckUpdateData> {
        val request = CheckUpdateRequest(plugins = plugins)
        return post("/api/plugins/check-updates", request)
    }

    suspend fun downloadPlugin(
        pluginId: String,
        version: String? = null,
        targetFile: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        try {
            val path = if (version != null) {
                "/api/plugins/$pluginId/download/$version"
            } else {
                "/api/plugins/$pluginId/download"
            }

            var startByte = 0L
            if (targetFile.exists()) {
                startByte = targetFile.length()
            }

            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .get()

            if (startByte > 0) {
                requestBuilder.addHeader("Range", "bytes=$startByte-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    val peek = runCatching { resp.peekBody(4096).string().trim() }.getOrNull()
                    val apiError = peek?.let { ApiEnvelopeParser.parseError(it) }
                    val apiErrorMessage = apiError?.let { ApiEnvelopeParser.formatApiError(it.apiCode, it.message) }

                    val detail = buildString {
                        append(Strings.error_download_failed.str())
                        append(" (HTTP ").append(resp.code).append(")")
                        val reason = apiErrorMessage
                            ?: resp.message.trim().takeIf { it.isNotBlank() }
                            ?: peek?.takeIf { it.isNotBlank() }?.take(200)
                        if (!reason.isNullOrBlank()) {
                            append(": ").append(reason)
                        }
                    }

                    Timber.tag(TAG).w(
                        "Download plugin failed: pluginId=%s, version=%s, http=%d, reason=%s, url=%s",
                        pluginId,
                        version ?: "",
                        resp.code,
                        apiErrorMessage ?: resp.message,
                        "$baseUrl$path"
                    )
                    if (!peek.isNullOrBlank()) {
                        Timber.tag(TAG).d("Download plugin failed response body: %s", peek.take(2048))
                    }

                    return@use ApiResult.Error(resp.code, detail)
                }

                val body = resp.body ?: run {
                    Timber.tag(TAG).e(
                        "Download plugin failed: empty response body (http=%d) url=%s",
                        resp.code,
                        "$baseUrl$path"
                    )
                    return@use ApiResult.Error(
                        -1,
                        Strings.error_download_failed.str()
                    )
                }

                val contentLength = body.contentLength()
                val total = if (resp.code == 206) {
                    val contentRange = resp.header("Content-Range")
                    contentRange?.substringAfter("/")?.toLongOrNull() ?: (startByte + contentLength)
                } else {
                    contentLength
                }

                val isResume = resp.code == 206
                RandomAccessFile(targetFile, "rw").use { raf ->
                    if (isResume) {
                        raf.seek(startByte)
                    } else {
                        raf.setLength(0)
                    }

                    val buffer = ByteArray(8192)
                    var downloaded = if (isResume) startByte else 0L
                    body.byteStream().use { inputStream ->
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            raf.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress?.invoke(downloaded, total)
                        }
                    }
                }

                val expectedHash = resp.header("X-Plugin-Hash")
                if (expectedHash != null) {
                    val actualHash = calculateSha256(targetFile)
                    // 服务器返回格式: "sha256:xxxxx"，需要提取实际哈希值
                    val expectedHashValue = if (expectedHash.contains(":")) {
                        expectedHash.substringAfter(":")
                    } else {
                        expectedHash
                    }
                    
                    if (!actualHash.equals(expectedHashValue, ignoreCase = true)) {
                        Timber.tag(TAG).w(
                            "Download plugin hash mismatch: pluginId=%s, version=%s, expected=%s, actual=%s",
                            pluginId,
                            version ?: "",
                            expectedHashValue,
                            actualHash
                        )
                        targetFile.delete()
                        return@use ApiResult.Error(
                            -1,
                            Strings.error_file_hash_mismatch.str()
                        )
                    }
                }

                ApiResult.Success(targetFile)
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Download plugin failed")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Download plugin unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    private suspend inline fun <reified T> postEmpty(
        path: String,
        accessToken: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = "{}".toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .post(jsonBody)
            accessToken?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            val response = client.newCall(requestBuilder.build()).execute()
            parseResponse(response)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "POST $path network error")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "POST $path unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    private suspend inline fun <reified T, reified B : Any> post(
        path: String,
        body: B,
        accessToken: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = json.encodeToString<B>(body).toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .post(jsonBody)

            accessToken?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            parseResponse(response)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "POST $path network error")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "POST $path unknown error")
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
}
