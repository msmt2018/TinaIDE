package com.wuxianggujun.tinaide.core.packages.api

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.OkHttpClientProvider
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryConfig
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryHttpClientFactory
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryProxyConfig
import com.wuxianggujun.tinaide.core.network.registry.GitHubRegistryProxySettings
import com.wuxianggujun.tinaide.core.network.registry.RegistryUrl
import com.wuxianggujun.tinaide.core.packages.model.DownloadInfo
import com.wuxianggujun.tinaide.core.packages.model.DownloadSource
import com.wuxianggujun.tinaide.core.packages.model.GUIPackage
import com.wuxianggujun.tinaide.core.packages.model.PackageCategory
import com.wuxianggujun.tinaide.core.packages.model.PackageVersion
import com.wuxianggujun.tinaide.core.packages.model.Platform
import com.wuxianggujun.tinaide.core.packages.model.PlatformPackage
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import java.io.IOException
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

class PackageApiClient private constructor(
    private val v2IndexUrls: List<RegistryUrl>,
    private val v1IndexUrls: List<RegistryUrl>,
    private val indexClient: OkHttpClient,
    private val proxySettings: GitHubRegistryProxySettings,
) {
    private val json = JsonSerializer.default
    private val indexMutex = Mutex()
    private val detailMutex = Mutex()
    private var cachedIndex: LoadedPackageRegistryIndex? = null
    private val cachedDetails = mutableMapOf<String, PackageRegistryDetail>()

    companion object {
        private const val TAG = "PackageApiClient"

        @Volatile
        private var instance: PackageApiClient? = null

        fun getInstance(): PackageApiClient {
            return instance ?: synchronized(this) {
                instance ?: createInstance().also { instance = it }
            }
        }

        fun getInstance(context: Context): PackageApiClient {
            val appContext = context.applicationContext
            val settings = GitHubRegistryProxyConfig.load(appContext)
            return synchronized(this) {
                instance
                    ?.takeIf { it.proxySettings == settings }
                    ?: createInstance(appContext, settings).also { instance = it }
            }
        }

        private fun createInstance(): PackageApiClient {
            return PackageApiClient(
                v2IndexUrls = GitHubRegistryConfig.packageIndexV2Urls(),
                v1IndexUrls = GitHubRegistryConfig.packageIndexUrls(),
                indexClient = OkHttpClientProvider.probe,
                proxySettings = GitHubRegistryProxySettings(),
            )
        }

        private fun createInstance(
            context: Context,
            settings: GitHubRegistryProxySettings,
        ): PackageApiClient {
            return PackageApiClient(
                v2IndexUrls = GitHubRegistryConfig.packageIndexV2Urls(),
                v1IndexUrls = GitHubRegistryConfig.packageIndexUrls(),
                indexClient = GitHubRegistryHttpClientFactory.probe(context),
                proxySettings = settings,
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
        search: String? = null,
    ): ApiResult<PackageListResponse> = withIndex { index ->
        val filtered = index.packages
            .asSequence()
            .filter { pkg -> category.isNullOrBlank() || pkg.category == category }
            .filter { pkg -> platform.isNullOrBlank() || pkg.hasPlatform(platform) }
            .filter { pkg ->
                val query = search?.trim().orEmpty()
                query.isBlank() ||
                    pkg.id.contains(query, ignoreCase = true) ||
                    pkg.name.contains(query, ignoreCase = true) ||
                    pkg.description?.contains(query, ignoreCase = true) == true
            }
            .sortedBy { it.name.lowercase() }
            .toList()

        val safePageSize = pageSize.coerceAtLeast(1)
        val safePage = page.coerceAtLeast(1)
        PackageListResponse(
            packages = filtered.drop((safePage - 1) * safePageSize).take(safePageSize),
            total = filtered.size,
            page = safePage,
            pageSize = safePageSize,
        )
    }

    suspend fun getCategories(): ApiResult<List<PackageCategory>> = withIndex { index ->
        index.categories.takeIf { it.isNotEmpty() } ?: deriveCategories(index.packages)
    }

    suspend fun getPackageDetail(packageId: String): ApiResult<GUIPackage> = withContext(Dispatchers.IO) {
        when (val indexResult = loadIndex()) {
            is ApiResult.Success -> when (val detailResult = resolvePackageDetail(indexResult.data, packageId)) {
                is ApiResult.Success -> ApiResult.Success(detailResult.data.pkg)
                is ApiResult.Error -> detailResult
                is ApiResult.NetworkError -> detailResult
            }
            is ApiResult.Error -> indexResult
            is ApiResult.NetworkError -> indexResult
        }
    }

    suspend fun getPackageVersions(packageId: String): ApiResult<PackageVersionsResponse> = withContext(Dispatchers.IO) {
        val index = when (val indexResult = loadIndex()) {
            is ApiResult.Success -> indexResult.data
            is ApiResult.Error -> return@withContext indexResult
            is ApiResult.NetworkError -> return@withContext indexResult
        }
        if (index.v1Index != null) {
            return@withContext withIndex { loaded ->
                loaded.versions[packageId]
                    ?: loaded.packages.firstOrNull { it.id == packageId }?.toVersions(packageId)
                    ?: throw NoSuchElementException(Strings.pkg_manager_error_package_versions_not_found.str(packageId))
            }
        }
        when (val detailResult = resolvePackageDetail(index, packageId)) {
            is ApiResult.Success -> {
                val detail = detailResult.data
                ApiResult.Success(detail.versions ?: detail.pkg.toVersions(packageId))
            }
            is ApiResult.Error -> detailResult
            is ApiResult.NetworkError -> detailResult
        }
    }

    suspend fun getDownloadInfo(packageId: String, versionId: Int): ApiResult<DownloadInfo> = withContext(Dispatchers.IO) {
        val index = when (val indexResult = loadIndex()) {
            is ApiResult.Success -> indexResult.data
            is ApiResult.Error -> return@withContext indexResult
            is ApiResult.NetworkError -> return@withContext indexResult
        }
        if (index.v1Index != null) {
            return@withContext withIndex { loaded ->
                loaded.downloads["$packageId:$versionId"]
                    ?.withResolvedSources(loaded.baseUrl)
                    ?: resolveDownloadInfo(packageId, versionId, loaded.baseUrl, loaded.versions[packageId], loaded.packages.firstOrNull { it.id == packageId })
                    ?: throw NoSuchElementException(
                        Strings.pkg_manager_error_download_info_not_found.str(packageId, versionId),
                    )
            }
        }
        when (val detailResult = resolvePackageDetail(index, packageId)) {
            is ApiResult.Success -> {
                val detail = detailResult.data
                ApiResult.Success(
                    detail.downloads["$packageId:$versionId"]
                        ?.withResolvedSources(index.baseUrl)
                        ?: resolveDownloadInfo(
                            packageId = packageId,
                            versionId = versionId,
                            baseUrl = index.baseUrl,
                            versions = detail.versions,
                            pkg = detail.pkg,
                        )
                        ?: return@withContext ApiResult.Error(
                            -1,
                            Strings.pkg_manager_error_download_info_not_found.str(packageId, versionId),
                        )
                )
            }
            is ApiResult.Error -> detailResult
            is ApiResult.NetworkError -> detailResult
        }
    }

    private suspend fun <T> withIndex(block: (LoadedPackageRegistryIndex) -> T): ApiResult<T> {
        return when (val result = loadIndex()) {
            is ApiResult.Success -> runCatching { ApiResult.Success(block(result.data)) }
                .getOrElse { error -> ApiResult.Error(-1, error.message ?: Strings.error_unknown.str()) }
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    private suspend fun loadIndex(): ApiResult<LoadedPackageRegistryIndex> = withContext(Dispatchers.IO) {
        cachedIndex?.let { return@withContext ApiResult.Success(it) }
        indexMutex.withLock {
            cachedIndex?.let { return@withLock ApiResult.Success(it) }
            val v2Result = loadIndexFromUrls(v2IndexUrls, "v2") { body, registryUrl ->
                LoadedPackageRegistryIndex(
                    v2Index = json.decodeFromString<PackageRegistryCatalog>(body),
                    baseUrl = registryUrl.endpoint.baseUrl,
                )
            }
            if (v2Result is ApiResult.Success) {
                cachedIndex = v2Result.data
                return@withLock v2Result
            }

            val v1Result = loadIndexFromUrls(v1IndexUrls, "v1") { body, registryUrl ->
                LoadedPackageRegistryIndex(
                    v1Index = json.decodeFromString<PackageRegistryIndex>(body),
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
        decode: (body: String, registryUrl: RegistryUrl) -> LoadedPackageRegistryIndex,
    ): ApiResult<LoadedPackageRegistryIndex> {
        var lastError: ApiResult<LoadedPackageRegistryIndex>? = null
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
                        "Loaded package registry %s via %s",
                        schemaLabel,
                        registryUrl.endpoint.name,
                    )
                    return ApiResult.Success(index)
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Load package registry %s failed via %s", schemaLabel, registryUrl.endpoint.name)
                lastError = ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Parse package registry %s failed via %s", schemaLabel, registryUrl.endpoint.name)
                lastError = ApiResult.Error(-1, e.message ?: Strings.error_response_parse_failed.str())
            }
        }
        return lastError ?: ApiResult.NetworkError(Strings.error_network_connection_failed.str())
    }

    private suspend fun resolvePackageDetail(
        index: LoadedPackageRegistryIndex,
        packageId: String,
    ): ApiResult<PackageRegistryDetail> {
        index.v1Index?.packages
            ?.firstOrNull { it.id == packageId }
            ?.let { pkg ->
                return ApiResult.Success(
                    PackageRegistryDetail(
                        pkg = pkg,
                        versions = index.v1Index.versions[packageId] ?: pkg.toVersions(packageId),
                        downloads = index.v1Index.downloads,
                    )
                )
            }

        val entry = index.v2Index?.packages
            ?.firstOrNull { it.id == packageId }
            ?: return ApiResult.Error(404, Strings.pkg_manager_error_package_not_found.str(packageId))
        val detailUrl = entry.detailUrl
            ?: return ApiResult.Error(-1, Strings.pkg_manager_error_package_not_found.str(packageId))

        cachedDetails[entry.id]?.let { return ApiResult.Success(it) }
        return detailMutex.withLock {
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
                            "Package detail request failed: HTTP ${resp.code}",
                        )
                    }
                    val body = resp.body?.string()
                        ?: return@withLock ApiResult.Error(-1, Strings.error_response_empty.str())
                    val detail = json.decodeFromString<PackageRegistryDetail>(body)
                    cachedDetails[detail.pkg.id] = detail
                    ApiResult.Success(detail)
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Load package detail failed: %s", packageId)
                ApiResult.NetworkError(e.message ?: Strings.error_network_connection_failed.str())
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Parse package detail failed: %s", packageId)
                ApiResult.Error(-1, e.message ?: Strings.error_response_parse_failed.str())
            }
        }
    }

    private fun resolveDownloadInfo(
        packageId: String,
        versionId: Int,
        baseUrl: String,
        versions: PackageVersionsResponse?,
        pkg: GUIPackage?,
    ): DownloadInfo? {
        val resolvedVersions = versions
            ?: pkg?.toVersions(packageId)
            ?: return null
        val version = resolvedVersions.allVersions().firstOrNull { it.id == versionId } ?: return null
        val sources = when {
            !version.downloadSources.isNullOrEmpty() -> version.downloadSources
            !version.downloadUrl.isNullOrBlank() -> listOf(
                DownloadSource(
                    id = 1,
                    name = "GitHub",
                    url = version.downloadUrl,
                    region = null,
                    priority = 100,
                    supportsRange = true,
                )
            )
            else -> emptyList()
        }.map { it.copy(url = GitHubRegistryConfig.resolveRawUrl(it.url, baseUrl)) }

        if (sources.isEmpty()) return null

        return DownloadInfo(
            packageId = packageId,
            version = version.version,
            platform = version.platform,
            installType = version.installType,
            size = version.downloadSize,
            checksum = version.checksum,
            sources = sources,
        )
    }

    private fun deriveCategories(packages: List<GUIPackage>): List<PackageCategory> {
        return packages.mapNotNull { it.category }
            .distinct()
            .sorted()
            .mapIndexed { index, category ->
                PackageCategory(
                    id = category,
                    name = category.replaceFirstChar { it.uppercase() },
                    sortOrder = index,
                )
            }
    }

    private fun GUIPackage.hasPlatform(platform: String): Boolean {
        return when (platform.lowercase()) {
            Platform.LINUX.name.lowercase() -> linux != null
            Platform.ANDROID.name.lowercase() -> android != null
            else -> true
        }
    }

    private fun GUIPackage.toVersions(packageId: String): PackageVersionsResponse {
        return PackageVersionsResponse(
            linux = linux?.let { pkg ->
                listOf(
                    PackageVersion(
                        id = 1,
                        packageId = packageId,
                        platform = Platform.LINUX,
                        version = pkg.version,
                        artifactType = pkg.artifactType,
                        installType = pkg.installType,
                        aptPackage = pkg.aptPackage,
                        downloadSize = pkg.size,
                        downloadUrl = pkg.downloadUrl,
                        downloadSources = pkg.downloadSources,
                        checksum = pkg.checksum,
                        abi = pkg.abi,
                        dependencies = pkg.dependencies,
                        releaseNotes = pkg.releaseNotes,
                        isLatest = true,
                    )
                )
            },
            android = android?.let { pkg ->
                listOf(
                    PackageVersion(
                        id = 2,
                        packageId = packageId,
                        platform = Platform.ANDROID,
                        version = pkg.version,
                        artifactType = pkg.artifactType,
                        installType = pkg.installType,
                        aptPackage = pkg.aptPackage,
                        downloadSize = pkg.size,
                        downloadUrl = pkg.downloadUrl,
                        downloadSources = pkg.downloadSources,
                        checksum = pkg.checksum,
                        abi = pkg.abi,
                        dependencies = pkg.dependencies,
                        releaseNotes = pkg.releaseNotes,
                        isLatest = true,
                    )
                )
            },
        )
    }

    private fun PackageVersionsResponse.allVersions(): List<PackageVersion> {
        return linux.orEmpty() + android.orEmpty()
    }

    private fun DownloadInfo.withResolvedSources(baseUrl: String): DownloadInfo {
        return copy(
            sources = sources.map { source ->
                source.copy(url = GitHubRegistryConfig.resolveRawUrl(source.url, baseUrl))
            }
        )
    }
}

@Serializable
data class PackageRegistryIndex(
    val packages: List<GUIPackage> = emptyList(),
    val categories: List<PackageCategory> = emptyList(),
    val versions: Map<String, PackageVersionsResponse> = emptyMap(),
    val downloads: Map<String, DownloadInfo> = emptyMap(),
)

@Serializable
data class PackageRegistryCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 2,
    @SerialName("generated_at")
    val generatedAt: String? = null,
    val packages: List<PackageRegistryCatalogEntry> = emptyList(),
    val categories: List<PackageCategory> = emptyList(),
)

@Serializable
data class PackageRegistryCatalogEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    @SerialName("icon_url")
    val iconUrl: String? = null,
    val homepage: String? = null,
    val linux: PlatformPackage? = null,
    val android: PlatformPackage? = null,
    @SerialName("detail_url")
    val detailUrl: String? = null,
) {
    fun toPackage(): GUIPackage {
        return GUIPackage(
            id = id,
            name = name,
            description = description,
            category = category,
            iconUrl = iconUrl,
            homepage = homepage,
            linux = linux,
            android = android,
        )
    }
}

@Serializable
data class PackageRegistryDetail(
    @SerialName("package")
    val pkg: GUIPackage,
    val versions: PackageVersionsResponse? = null,
    val downloads: Map<String, DownloadInfo> = emptyMap(),
)

data class LoadedPackageRegistryIndex(
    val v1Index: PackageRegistryIndex? = null,
    val v2Index: PackageRegistryCatalog? = null,
    val baseUrl: String,
) {
    val packages: List<GUIPackage>
        get() = v2Index?.packages?.map { it.toPackage() } ?: v1Index?.packages.orEmpty()
    val categories: List<PackageCategory>
        get() = v2Index?.categories ?: v1Index?.categories.orEmpty()
    val versions: Map<String, PackageVersionsResponse>
        get() = v1Index?.versions.orEmpty()
    val downloads: Map<String, DownloadInfo>
        get() = v1Index?.downloads.orEmpty()
}

@Serializable
data class PackageListResponse(
    val packages: List<GUIPackage>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
)

@Serializable
data class PackageVersionsResponse(
    val linux: List<PackageVersion>? = null,
    val android: List<PackageVersion>? = null,
)
