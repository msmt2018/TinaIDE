package com.wuxianggujun.tinaide.plugin.marketplace

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryConfig
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryHttpClientFactory
import com.wuxianggujun.tinaide.core.network.registry.RegistryUrl
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class PluginMarketplaceApi private constructor(
    private val v2IndexUrls: List<RegistryUrl>,
    private val v1IndexUrls: List<RegistryUrl>,
    private val indexClient: OkHttpClient,
    private val downloadClient: OkHttpClient,
) {
    private val json = JsonSerializer.default
    private val indexMutex = Mutex()
    private val detailMutex = Mutex()
    private var cachedIndex: LoadedPluginRegistryIndex? = null
    private val cachedDetails = mutableMapOf<String, PluginDetail>()

    companion object {
        private const val TAG = "PluginMarketplaceApi"

        fun create(context: Context): PluginMarketplaceApi {
            return PluginMarketplaceApi(
                v2IndexUrls = GitHubRegistryConfig.pluginIndexV2Urls(),
                v1IndexUrls = GitHubRegistryConfig.pluginIndexUrls(),
                indexClient = GitHubRegistryHttpClientFactory.probe(context.applicationContext),
                downloadClient = GitHubRegistryHttpClientFactory.download(context.applicationContext),
            )
        }
    }

    suspend fun listPlugins(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null,
        sort: String? = null,
    ): ApiResult<PluginListData> = withIndex { index ->
        val filtered = index.plugins
            .asSequence()
            .filter { plugin -> category.isNullOrBlank() || plugin.category == category }
            .filter { plugin ->
                val query = search?.trim().orEmpty()
                query.isBlank() ||
                    plugin.name.contains(query, ignoreCase = true) ||
                    plugin.pluginId.contains(query, ignoreCase = true) ||
                    plugin.description?.contains(query, ignoreCase = true) == true ||
                    plugin.tags.any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(pluginSortComparator(sort))
            .toList()

        val safeLimit = limit.coerceAtLeast(1)
        val safePage = page.coerceAtLeast(1)
        val total = filtered.size
        val totalPages = if (total == 0) 1 else ((total + safeLimit - 1) / safeLimit)
        val pageItems = filtered
            .drop((safePage - 1) * safeLimit)
            .take(safeLimit)
            .map { it.toSummary() }

        PluginListData(
            plugins = pageItems,
            pagination = Pagination(
                page = safePage,
                limit = safeLimit,
                total = total.toLong(),
                totalPages = totalPages,
            ),
        )
    }

    suspend fun getPluginDetail(pluginId: String): ApiResult<PluginDetail> = withContext(Dispatchers.IO) {
        when (val indexResult = loadIndex()) {
            is ApiResult.Success -> resolvePluginDetail(indexResult.data, pluginId)
            is ApiResult.Error -> indexResult
            is ApiResult.NetworkError -> indexResult
        }
    }

    suspend fun checkUpdates(
        plugins: List<CheckUpdateItem>,
    ): ApiResult<CheckUpdateData> = withContext(Dispatchers.IO) {
        val index = when (val indexResult = loadIndex()) {
            is ApiResult.Success -> indexResult.data
            is ApiResult.Error -> return@withContext indexResult
            is ApiResult.NetworkError -> return@withContext indexResult
        }
        val updates = plugins.mapNotNull { installed ->
            val remote = when (val detailResult = resolvePluginDetail(index, installed.pluginId)) {
                is ApiResult.Success -> detailResult.data
                else -> return@mapNotNull null
            }
            val latest = remote.latestVersionEntry() ?: return@mapNotNull null
            if (!isNewerVersion(latest.version, installed.version)) return@mapNotNull null
            PluginUpdateInfo(
                pluginId = installed.pluginId,
                currentVersion = installed.version,
                latestVersion = latest.version,
                downloadUrl = latest.downloadUrl
                    ?.let { GitHubRegistryConfig.resolveRawUrl(it, index.baseUrl) }
                    .orEmpty(),
                changelog = latest.changelog,
                fileSize = latest.fileSize,
            )
        }
        ApiResult.Success(CheckUpdateData(updates))
    }

    suspend fun downloadPlugin(
        pluginId: String,
        version: String? = null,
        targetFile: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        try {
            val index = when (val indexResult = loadIndex()) {
                is ApiResult.Success -> indexResult.data
                is ApiResult.Error -> return@withContext indexResult
                is ApiResult.NetworkError -> return@withContext indexResult
            }
            val plugin = when (val detailResult = resolvePluginDetail(index, pluginId)) {
                is ApiResult.Success -> detailResult.data
                is ApiResult.Error -> return@withContext detailResult
                is ApiResult.NetworkError -> return@withContext detailResult
            }
            val pluginVersion = plugin.resolveVersion(version)
                ?: return@withContext ApiResult.Error(
                    404,
                    Strings.plugin_marketplace_error_plugin_version_not_found.str(version ?: "latest"),
                )
            val downloadUrl = pluginVersion.downloadUrl
                ?.let { GitHubRegistryConfig.resolveRawUrl(it, index.baseUrl) }
                ?: return@withContext ApiResult.Error(
                    -1,
                    Strings.plugin_marketplace_error_download_url_missing.str(pluginId),
                )

            downloadFile(
                url = downloadUrl,
                targetFile = targetFile,
                expectedHash = pluginVersion.fileHash,
                onProgress = onProgress,
            )
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Download plugin failed")
            ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Download plugin unknown error")
            ApiResult.Error(-1, e.message ?: Strings.error_unknown.str())
        }
    }

    private suspend fun <T> withIndex(block: (LoadedPluginRegistryIndex) -> T): ApiResult<T> {
        return when (val result = loadIndex()) {
            is ApiResult.Success -> runCatching { ApiResult.Success(block(result.data)) }
                .getOrElse { error -> ApiResult.Error(-1, error.message ?: Strings.error_unknown.str()) }
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    private suspend fun loadIndex(): ApiResult<LoadedPluginRegistryIndex> = withContext(Dispatchers.IO) {
        cachedIndex?.let { return@withContext ApiResult.Success(it) }
        indexMutex.withLock {
            cachedIndex?.let { return@withLock ApiResult.Success(it) }
            val v2Result = loadIndexFromUrls(v2IndexUrls, "v2") { body, registryUrl ->
                LoadedPluginRegistryIndex(
                    v2Index = json.decodeFromString<PluginRegistryCatalog>(body),
                    baseUrl = registryUrl.endpoint.baseUrl,
                )
            }
            if (v2Result is ApiResult.Success) {
                cachedIndex = v2Result.data
                return@withLock v2Result
            }

            val v1Result = loadIndexFromUrls(v1IndexUrls, "v1") { body, registryUrl ->
                LoadedPluginRegistryIndex(
                    v1Index = json.decodeFromString<PluginRegistryIndex>(body),
                    baseUrl = registryUrl.endpoint.baseUrl,
                )
            }
            if (v1Result is ApiResult.Success) {
                cachedIndex = v1Result.data
                return@withLock v1Result
            }

            v1Result
        }
    }

    private fun loadIndexFromUrls(
        urls: List<RegistryUrl>,
        schemaLabel: String,
        decode: (body: String, registryUrl: RegistryUrl) -> LoadedPluginRegistryIndex,
    ): ApiResult<LoadedPluginRegistryIndex> {
        var lastError: ApiResult<LoadedPluginRegistryIndex>? = null
        for (registryUrl in urls) {
            try {
                val response = indexClient.newCall(
                    Request.Builder()
                        .url(registryUrl.url)
                        .get()
                        .build()
                ).execute()
                response.use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful) {
                        lastError = ApiResult.Error(
                            resp.code,
                            "Registry $schemaLabel request failed via ${registryUrl.endpoint.name}: HTTP ${resp.code}",
                        )
                        return@use
                    }
                    if (body.isNullOrBlank()) {
                        lastError = ApiResult.Error(-1, Strings.error_response_empty.str())
                        return@use
                    }
                    val index = decode(body, registryUrl)
                    Timber.tag(TAG).i(
                        "Loaded plugin registry %s via %s",
                        schemaLabel,
                        registryUrl.endpoint.name,
                    )
                    return ApiResult.Success(index)
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Load plugin registry %s failed via %s", schemaLabel, registryUrl.endpoint.name)
                lastError = ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Parse plugin registry %s failed via %s", schemaLabel, registryUrl.endpoint.name)
                lastError = ApiResult.Error(-1, e.message ?: Strings.error_response_parse_failed.str())
            }
        }
        return lastError ?: ApiResult.NetworkError(Strings.error_network_connection_failed.str())
    }

    private suspend fun resolvePluginDetail(
        index: LoadedPluginRegistryIndex,
        pluginId: String,
    ): ApiResult<PluginDetail> {
        index.v1Index?.plugins
            ?.firstOrNull { it.pluginId == pluginId || it.id == pluginId }
            ?.let { return ApiResult.Success(it.toDetail()) }

        val entry = index.v2Index?.plugins
            ?.firstOrNull { it.pluginId == pluginId || it.id == pluginId }
            ?: return ApiResult.Error(404, Strings.plugin_marketplace_error_plugin_not_found.str(pluginId))

        val detailUrl = entry.detailUrl
            ?: return ApiResult.Error(-1, Strings.plugin_marketplace_error_download_url_missing.str(pluginId))

        cachedDetails[entry.pluginId]?.let { return ApiResult.Success(it) }
        cachedDetails[entry.id]?.let { return ApiResult.Success(it) }

        return detailMutex.withLock {
            cachedDetails[entry.pluginId]?.let { return@withLock ApiResult.Success(it) }
            cachedDetails[entry.id]?.let { return@withLock ApiResult.Success(it) }
            val resolvedUrl = GitHubRegistryConfig.resolveRawUrl(detailUrl, index.baseUrl)
            try {
                val response = indexClient.newCall(
                    Request.Builder()
                        .url(resolvedUrl)
                        .get()
                        .build()
                ).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withLock ApiResult.Error(
                            resp.code,
                            "Plugin detail request failed: HTTP ${resp.code}",
                        )
                    }
                    val body = resp.body?.string()
                        ?: return@withLock ApiResult.Error(-1, Strings.error_response_empty.str())
                    val detail = json.decodeFromString<PluginDetail>(body)
                    cachedDetails[detail.pluginId] = detail
                    cachedDetails[detail.id] = detail
                    ApiResult.Success(detail)
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Load plugin detail failed: %s", pluginId)
                ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Parse plugin detail failed: %s", pluginId)
                ApiResult.Error(-1, e.message ?: Strings.error_response_parse_failed.str())
            }
        }
    }

    private fun downloadFile(
        url: String,
        targetFile: File,
        expectedHash: String?,
        onProgress: ((downloaded: Long, total: Long) -> Unit)?,
    ): ApiResult<File> {
        var startByte = 0L
        if (targetFile.exists()) {
            startByte = targetFile.length()
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        if (startByte > 0) {
            requestBuilder.addHeader("Range", "bytes=$startByte-")
        }

        val response = downloadClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                return ApiResult.Error(resp.code, "${Strings.error_download_failed.str()} (HTTP ${resp.code})")
            }

            val body = resp.body ?: return ApiResult.Error(-1, Strings.error_download_failed.str())
            val contentLength = body.contentLength()
            val total = if (resp.code == 206) {
                resp.header("Content-Range")?.substringAfter("/")?.toLongOrNull() ?: (startByte + contentLength)
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

            if (!expectedHash.isNullOrBlank()) {
                val actualHash = calculateSha256(targetFile)
                val expectedHashValue = expectedHash.substringAfter("sha256:", expectedHash)
                if (!actualHash.equals(expectedHashValue, ignoreCase = true)) {
                    targetFile.delete()
                    return ApiResult.Error(-1, Strings.error_file_hash_mismatch.str())
                }
            }

            return ApiResult.Success(targetFile)
        }
    }

    private fun pluginSortComparator(sort: String?): Comparator<PluginRegistryCatalogEntry> {
        return when (sort) {
            PluginSortType.NEWEST.value -> compareByDescending { it.createdAt }
            PluginSortType.UPDATED.value -> compareByDescending { it.updatedAt }
            else -> compareByDescending { it.updatedAt }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
            val left = remoteParts.getOrElse(index) { 0 }
            val right = localParts.getOrElse(index) { 0 }
            if (left > right) return true
            if (left < right) return false
        }
        return remote != local
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Serializable
data class PluginRegistryIndex(
    val plugins: List<PluginRegistryEntry> = emptyList(),
)

@Serializable
data class PluginRegistryCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 2,
    @SerialName("generated_at")
    val generatedAt: String? = null,
    val plugins: List<PluginRegistryCatalogEntry> = emptyList(),
)

data class LoadedPluginRegistryIndex(
    val v1Index: PluginRegistryIndex? = null,
    val v2Index: PluginRegistryCatalog? = null,
    val baseUrl: String,
) {
    val plugins: List<PluginRegistryCatalogEntry>
        get() = v2Index?.plugins ?: v1Index?.plugins?.map { it.toCatalogEntry() }.orEmpty()
}

@Serializable
data class PluginRegistryCatalogEntry(
    val id: String,
    @SerialName("plugin_id")
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("icon_url")
    val iconUrl: String? = null,
    val publisher: PluginPublisher,
    @SerialName("latest_version")
    val latestVersion: String? = null,
    @SerialName("detail_url")
    val detailUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
) {
    fun toSummary(): PluginSummary {
        return PluginSummary(
            id = id,
            pluginId = pluginId,
            name = name,
            description = description,
            category = category,
            tags = tags,
            iconUrl = iconUrl,
            publisher = publisher,
            latestVersion = latestVersion,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
data class PluginRegistryEntry(
    val id: String,
    @SerialName("plugin_id")
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("icon_url")
    val iconUrl: String? = null,
    @SerialName("repository_url")
    val repositoryUrl: String? = null,
    @SerialName("homepage_url")
    val homepageUrl: String? = null,
    val license: String? = null,
    val publisher: PluginPublisher,
    val versions: List<PluginVersion> = emptyList(),
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun latestVersionEntry(): PluginVersion? {
        return versions.maxWithOrNull(
            compareBy<PluginVersion> { it.versionCode }.thenBy { it.version }
        )
    }

    fun resolveVersion(version: String?): PluginVersion? {
        return if (version.isNullOrBlank()) {
            latestVersionEntry()
        } else {
            versions.firstOrNull { it.version == version }
        }
    }

    fun toCatalogEntry(): PluginRegistryCatalogEntry {
        return PluginRegistryCatalogEntry(
            id = id,
            pluginId = pluginId,
            name = name,
            description = description,
            category = category,
            tags = tags,
            iconUrl = iconUrl,
            publisher = publisher,
            latestVersion = latestVersionEntry()?.version,
            detailUrl = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun toSummary(): PluginSummary {
        return PluginSummary(
            id = id,
            pluginId = pluginId,
            name = name,
            description = description,
            category = category,
            tags = tags,
            iconUrl = iconUrl,
            publisher = publisher,
            latestVersion = latestVersionEntry()?.version,
            updatedAt = updatedAt,
        )
    }

    fun toDetail(): PluginDetail {
        return PluginDetail(
            id = id,
            pluginId = pluginId,
            name = name,
            description = description,
            category = category,
            tags = tags,
            iconUrl = iconUrl,
            repositoryUrl = repositoryUrl,
            homepageUrl = homepageUrl,
            license = license,
            publisher = publisher,
            versions = versions.sortedWith(compareByDescending<PluginVersion> { it.versionCode }.thenByDescending { it.version }),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

private fun PluginDetail.resolveVersion(version: String?): PluginVersion? {
    return if (version.isNullOrBlank()) {
        latestVersionEntry()
    } else {
        versions.firstOrNull { it.version == version }
    }
}
