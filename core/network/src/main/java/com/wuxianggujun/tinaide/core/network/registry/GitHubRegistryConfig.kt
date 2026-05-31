package com.wuxianggujun.tinaide.core.network.registry

object GitHubRegistryConfig {
    const val OWNER = "wuxianggujun"
    const val REPOSITORY = "TinaIDE-Registry"
    const val BRANCH = "main"

    const val GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com/$OWNER/$REPOSITORY/$BRANCH"
    const val JSDELIVR_BASE_URL = "https://cdn.jsdelivr.net/gh/$OWNER/$REPOSITORY@$BRANCH"

    const val RAW_BASE_URL = GITHUB_RAW_BASE_URL
    const val PRIMARY_BASE_URL = GITHUB_RAW_BASE_URL

    const val PLUGINS_INDEX_PATH = "plugins/index.json"
    const val PACKAGES_INDEX_PATH = "packages/index.json"
    const val PLUGINS_INDEX_URL = "$PRIMARY_BASE_URL/$PLUGINS_INDEX_PATH"
    const val PACKAGES_INDEX_URL = "$PRIMARY_BASE_URL/$PACKAGES_INDEX_PATH"

    val REGISTRY_ENDPOINTS: List<RegistryEndpoint> = listOf(
        RegistryEndpoint(name = "GitHub Raw", baseUrl = GITHUB_RAW_BASE_URL),
        RegistryEndpoint(name = "jsDelivr CDN", baseUrl = JSDELIVR_BASE_URL),
    )

    fun pluginIndexUrls(): List<RegistryUrl> = indexUrls(PLUGINS_INDEX_PATH)

    fun packageIndexUrls(): List<RegistryUrl> = indexUrls(PACKAGES_INDEX_PATH)

    fun resolveRawUrl(urlOrPath: String, baseUrl: String = PRIMARY_BASE_URL): String {
        val value = urlOrPath.trim()
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "${baseUrl.trimEnd('/')}/${value.removePrefix("/")}"
        }
    }

    private fun indexUrls(path: String): List<RegistryUrl> {
        return REGISTRY_ENDPOINTS.map { endpoint ->
            RegistryUrl(
                endpoint = endpoint,
                url = resolveRawUrl(path, endpoint.baseUrl),
            )
        }
    }
}

data class RegistryEndpoint(
    val name: String,
    val baseUrl: String,
)

data class RegistryUrl(
    val endpoint: RegistryEndpoint,
    val url: String,
)
