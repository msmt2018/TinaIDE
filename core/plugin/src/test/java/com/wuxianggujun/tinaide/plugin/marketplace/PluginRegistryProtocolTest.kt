package com.wuxianggujun.tinaide.plugin.marketplace

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.registry.RegistryEndpoint
import com.wuxianggujun.tinaide.core.network.registry.RegistryUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class PluginRegistryProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun catalogEntry_shouldDeserializeLightweightV2IndexItem() {
        val catalog = json.decodeFromString<PluginRegistryCatalog>(
            """
            {
              "schema_version": 2,
              "generated_at": "2026-06-06T00:00:00Z",
              "plugins": [
                {
                  "id": "tinaide.plugin.example",
                  "plugin_id": "tinaide.plugin.example",
                  "name": "Example",
                  "description": "Small summary",
                  "category": "tool",
                  "tags": ["tool"],
                  "publisher": {
                    "id": "tinaide",
                    "display_name": "TinaIDE"
                  },
                  "latest_version": "1.0.0",
                  "detail_url": "plugins/tinaide.plugin.example/plugin.json",
                  "created_at": "2026-06-01T00:00:00Z",
                  "updated_at": "2026-06-06T00:00:00Z"
                }
              ]
            }
            """.trimIndent()
        )

        val entry = catalog.plugins.single()
        val summary = entry.toSummary()

        assertThat(catalog.schemaVersion).isEqualTo(2)
        assertThat(entry.detailUrl).isEqualTo("plugins/tinaide.plugin.example/plugin.json")
        assertThat(summary.pluginId).isEqualTo("tinaide.plugin.example")
        assertThat(summary.latestVersion).isEqualTo("1.0.0")
        assertThat(summary.publisher.displayName).isEqualTo("TinaIDE")
    }

    @Test
    fun api_shouldLoadV2CatalogAndFetchDetailOnDemand(): Unit = runBlocking {
        val baseUrl = "https://registry.test"
        val v2IndexUrl = registryUrl(baseUrl, "plugins/index.v2.json")
        val detailUrl = "$baseUrl/plugins/tinaide.plugin.example/plugin.json"
        val interceptor = FakeRegistryInterceptor(
            mapOf(
                v2IndexUrl.url to RegistryResponse(
                    body = """
                    {
                      "schema_version": 2,
                      "plugins": [
                        {
                          "id": "tinaide.plugin.example",
                          "plugin_id": "tinaide.plugin.example",
                          "name": "Example",
                          "publisher": {
                            "id": "tinaide",
                            "display_name": "TinaIDE"
                          },
                          "latest_version": "1.1.0",
                          "detail_url": "plugins/tinaide.plugin.example/plugin.json",
                          "created_at": "2026-06-01T00:00:00Z",
                          "updated_at": "2026-06-06T00:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent()
                ),
                detailUrl to RegistryResponse(
                    body = """
                    {
                      "id": "tinaide.plugin.example",
                      "plugin_id": "tinaide.plugin.example",
                      "name": "Example",
                      "publisher": {
                        "id": "tinaide",
                        "display_name": "TinaIDE"
                      },
                      "versions": [
                        {
                          "version": "1.1.0",
                          "version_code": 2,
                          "file_size": 256,
                          "download_url": "plugins/tinaide.plugin.example/1.1.0/example.tinaplug",
                          "created_at": "2026-06-06T00:00:00Z"
                        }
                      ],
                      "created_at": "2026-06-01T00:00:00Z",
                      "updated_at": "2026-06-06T00:00:00Z"
                    }
                    """.trimIndent()
                ),
            )
        )
        val api = pluginApi(v2IndexUrl, interceptor.client())

        val result = api.getPluginDetail("tinaide.plugin.example")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val detail = (result as ApiResult.Success).data
        assertThat(detail.latestVersionEntry()?.version).isEqualTo("1.1.0")
        assertThat(interceptor.requestedUrls)
            .containsExactly(v2IndexUrl.url, detailUrl)
            .inOrder()
    }

    @Test
    fun api_shouldFailWithoutRequestingV1IndexWhenV2CatalogUnavailable(): Unit = runBlocking {
        val baseUrl = "https://registry.test"
        val v2IndexUrl = registryUrl(baseUrl, "plugins/index.v2.json")
        val v1IndexUrl = registryUrl(baseUrl, "plugins/index.json")
        val interceptor = FakeRegistryInterceptor(
            mapOf(
                v1IndexUrl.url to RegistryResponse(
                    body = """
                    {
                      "plugins": [
                        {
                          "id": "tinaide.plugin.example",
                          "plugin_id": "tinaide.plugin.example",
                          "name": "Example",
                          "publisher": {
                            "id": "tinaide",
                            "display_name": "TinaIDE"
                          },
                          "versions": [
                            {
                              "version": "1.0.0",
                              "version_code": 1,
                              "file_size": 128,
                              "created_at": "2026-06-01T00:00:00Z"
                            }
                          ],
                          "created_at": "2026-06-01T00:00:00Z",
                          "updated_at": "2026-06-06T00:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent()
                ),
                v2IndexUrl.url to RegistryResponse(code = 404),
            )
        )
        val api = pluginApi(v2IndexUrl, interceptor.client())

        val result = api.getPluginDetail("tinaide.plugin.example")

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat(interceptor.requestedUrls)
            .containsExactly(v2IndexUrl.url)
            .inOrder()
    }

    private fun registryUrl(baseUrl: String, path: String): RegistryUrl {
        val endpoint = RegistryEndpoint(name = "test", baseUrl = baseUrl)
        return RegistryUrl(endpoint = endpoint, url = "$baseUrl/$path")
    }

    private fun pluginApi(
        v2IndexUrl: RegistryUrl,
        client: OkHttpClient,
    ): PluginMarketplaceApi {
        val constructor = PluginMarketplaceApi::class.java.getDeclaredConstructor(
            List::class.java,
            OkHttpClient::class.java,
            OkHttpClient::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            listOf(v2IndexUrl),
            client,
            client,
        ) as PluginMarketplaceApi
    }

    private data class RegistryResponse(
        val code: Int = 200,
        val body: String = "",
    )

    private class FakeRegistryInterceptor(
        private val responses: Map<String, RegistryResponse>,
    ) : Interceptor {
        val requestedUrls = mutableListOf<String>()

        fun client(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(this)
            .build()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            requestedUrls += url
            val registryResponse = responses[url] ?: RegistryResponse(code = 404)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(registryResponse.code)
                .message(if (registryResponse.code in 200..299) "OK" else "Error")
                .body(registryResponse.body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
