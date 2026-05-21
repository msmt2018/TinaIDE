package com.wuxianggujun.tinaide.ai.tools.search

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.error
import com.wuxianggujun.tinaide.ai.tools.toolCall
import com.wuxianggujun.tinaide.core.i18n.AppStrings
import io.mockk.every
import io.mockk.mockk
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchToolsExecutionTest {

    @Before
    fun setUp() {
        resetAppStrings()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getString(any<Int>()) } answers { "string-${firstArg<Int>()}" }
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val formatArgs = invocation.args.drop(1)
                .flatMap { arg -> if (arg is Array<*>) arg.asList() else listOf(arg) }
                .joinToString(separator = "|")
            "string-${invocation.args.first()}-$formatArgs"
        }
        AppStrings.initialize(context)
    }

    @After
    fun tearDown() {
        resetAppStrings()
    }

    @Test
    fun `github search formats repository results and clamps max results`(): Unit = runBlocking {
        val originalClient = GitHubSearchTool.client
        var capturedRequest: Request? = null
        GitHubSearchTool.client = fakeClient { request ->
            capturedRequest = request
            jsonResponse(
                request,
                """
                {
                  "total_count": 42,
                  "items": [
                    {
                      "full_name": "owner/repo",
                      "description": "Useful Kotlin library",
                      "stargazers_count": 123,
                      "forks_count": 4,
                      "language": "Kotlin",
                      "html_url": "https://github.com/owner/repo",
                      "updated_at": "2026-01-02T00:00:00Z"
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        try {
            val result = GitHubSearchTool.execute(
                toolCall(
                    GitHubSearchTool.name,
                    """{"query":"coroutines","language":"Kotlin","sort":"stars","max_results":99}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.queryParameter("per_page")).isEqualTo("30")
            assertThat(capturedRequest?.url?.queryParameter("sort")).isEqualTo("stars")
            assertThat(capturedRequest?.url?.queryParameter("order")).isEqualTo("desc")
            assertThat(result.content).contains("owner/repo")
            assertThat(result.content).contains("Useful Kotlin library")
            assertThat(result.metadata["searchType"]).isEqualTo("repositories")
            assertThat(result.metadata["totalCount"]).isEqualTo(42)
            assertThat(result.metadata["resultCount"]).isEqualTo(1)
            assertThat(result.metadata["query"]).isEqualTo("coroutines language:Kotlin")
        } finally {
            GitHubSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search duckduckgo formats abstract related topics and site query`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        var capturedRequest: Request? = null
        WebSearchTool.client = fakeClient { request ->
            capturedRequest = request
            jsonResponse(
                request,
                """
                {
                  "AbstractText": "Kotlin is a concise programming language.",
                  "AbstractSource": "DuckDuckGo",
                  "AbstractURL": "https://kotlinlang.org",
                  "RelatedTopics": [
                    {
                      "Text": "Kotlin documentation",
                      "FirstURL": "https://kotlinlang.org/docs"
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        try {
            val result = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"duckduckgo","site":"kotlinlang.org","max_results":1}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.host).isEqualTo("api.duckduckgo.com")
            assertThat(capturedRequest?.url?.queryParameter("q")).isEqualTo("kotlin site:kotlinlang.org")
            assertThat(result.content).contains("Kotlin is a concise programming language.")
            assertThat(result.content).contains("Kotlin documentation")
            assertThat(result.metadata["searchEngine"]).isEqualTo("duckduckgo")
            assertThat(result.metadata["query"]).isEqualTo("kotlin site:kotlinlang.org")
            assertThat(result.metadata["hasAbstract"]).isEqualTo(true)
            assertThat(result.metadata["relatedTopicsCount"]).isEqualTo(1)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search duckduckgo formats nested related topic groups`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request ->
            jsonResponse(
                request,
                """
                {
                  "RelatedTopics": [
                    {
                      "Name": "Kotlin",
                      "Topics": [
                        {
                          "Text": "Nested Kotlin topic",
                          "FirstURL": "https://example.com/nested"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        try {
            val result = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"duckduckgo","max_results":5}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(result.content).contains("Nested Kotlin topic")
            assertThat(result.content).contains("https://example.com/nested")
            assertThat(result.metadata["relatedTopicsCount"]).isEqualTo(1)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `github search formats code issues and users results`(): Unit = runBlocking {
        val originalClient = GitHubSearchTool.client
        GitHubSearchTool.client = fakeClient { request ->
            when (request.url.encodedPath) {
                "/search/code" -> jsonResponse(
                    request,
                    """
                    {
                      "total_count": 1,
                      "items": [
                        {
                          "name": "Main.kt",
                          "path": "src/Main.kt",
                          "html_url": "https://github.com/owner/repo/blob/main/src/Main.kt",
                          "repository": {"full_name": "owner/repo"}
                        }
                      ]
                    }
                    """.trimIndent()
                )
                "/search/issues" -> jsonResponse(
                    request,
                    """
                    {
                      "total_count": 1,
                      "items": [
                        {
                          "title": "Bug title",
                          "state": "open",
                          "number": 12,
                          "html_url": "https://github.com/owner/repo/issues/12",
                          "created_at": "2026-03-04T00:00:00Z",
                          "user": {"login": "alice"}
                        }
                      ]
                    }
                    """.trimIndent()
                )
                "/search/users" -> jsonResponse(
                    request,
                    """
                    {
                      "total_count": 1,
                      "items": [
                        {
                          "login": "octocat",
                          "type": "User",
                          "html_url": "https://github.com/octocat"
                        }
                      ]
                    }
                    """.trimIndent()
                )
                else -> jsonResponse(request, """{"total_count":0,"items":[]}""")
            }
        }

        try {
            val code = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin","search_type":"code"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val issues = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin","search_type":"issues"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val users = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin","search_type":"users"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(code.content).contains("Main.kt")
            assertThat(code.content).contains("src/Main.kt")
            assertThat(code.metadata["searchType"]).isEqualTo("code")
            assertThat(issues.content).contains("#12 - Bug title [open]")
            assertThat(issues.content).contains("alice")
            assertThat(issues.metadata["searchType"]).isEqualTo("issues")
            assertThat(users.content).contains("octocat (User)")
            assertThat(users.metadata["searchType"]).isEqualTo("users")
        } finally {
            GitHubSearchTool.client = originalClient
        }
    }

    @Test
    fun `github search handles empty results and api errors`(): Unit = runBlocking {
        val originalClient = GitHubSearchTool.client
        GitHubSearchTool.client = fakeClient { request ->
            if (request.url.queryParameter("q") == "empty") {
                jsonResponse(request, """{"total_count":0,"items":[]}""")
            } else {
                jsonResponse(request, """{"message":"rate limited"}""", code = 403, message = "Forbidden")
            }
        }

        try {
            val empty = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"empty"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val rateLimited = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"limited"}"""),
                ToolExecutionContext()
            )

            assertThat(empty.content).contains("empty")
            assertThat(rateLimited).isInstanceOf(ToolExecutionResult.Error::class.java)
        } finally {
            GitHubSearchTool.client = originalClient
        }
    }

    @Test
    fun `search tools validate blank queries without network`(): Unit = runBlocking {
        assertThat(
            GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"   "}"""),
                ToolExecutionContext()
            )
        ).isInstanceOf(ToolExecutionResult.Error::class.java)

        assertThat(
            WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"   "}"""),
                ToolExecutionContext()
            )
        ).isInstanceOf(ToolExecutionResult.Error::class.java)
    }

    @Test
    fun `github search maps auth invalid unknown type and generic errors`(): Unit = runBlocking {
        val originalClient = GitHubSearchTool.client
        GitHubSearchTool.client = fakeClient { request ->
            when (request.url.queryParameter("q")) {
                "auth" -> jsonResponse(request, """{"message":"auth"}""", code = 401, message = "Unauthorized")
                "invalid" -> jsonResponse(request, """{"message":"bad query"}""", code = 422, message = "Invalid")
                "custom" -> jsonResponse(
                    request,
                    """
                    {
                      "total_count": 1,
                      "items": [{"name":"ignored"}]
                    }
                    """.trimIndent()
                )
                else -> jsonResponse(request, """{"message":"server"}""", code = 500, message = "Server Error")
            }
        }

        try {
            val auth = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"auth"}"""),
                ToolExecutionContext()
            )
            val invalid = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"invalid"}"""),
                ToolExecutionContext()
            )
            val generic = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"server"}"""),
                ToolExecutionContext()
            )
            val unknownType = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"custom","search_type":"custom"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(auth).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(invalid).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(generic).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(unknownType.content).contains("custom")
            assertThat(unknownType.metadata["searchType"]).isEqualTo("custom")
        } finally {
            GitHubSearchTool.client = originalClient
        }
    }

    @Test
    fun `github search maps unknown host and malformed success bodies to errors`(): Unit = runBlocking {
        val originalClient = GitHubSearchTool.client
        GitHubSearchTool.client = fakeClient { throw UnknownHostException("offline") }

        try {
            val offline = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin"}"""),
                ToolExecutionContext()
            )

            GitHubSearchTool.client = fakeClient { request ->
                jsonResponse(request, "not-json")
            }

            val malformed = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin"}"""),
                ToolExecutionContext()
            )

            assertThat(offline).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(malformed).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(malformed.error().message).contains("JSONException")
        } finally {
            GitHubSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search baidu formats html results`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        var capturedRequest: Request? = null
        WebSearchTool.client = fakeClient { request ->
            capturedRequest = request
            textResponse(
                request,
                """
                <html>
                  <body>
                    <h3 class="t"><a href="https://example.com/baidu">Baidu Kotlin</a></h3>
                    <div class="c-abstract">Baidu snippet</div>
                  </body>
                </html>
                """.trimIndent()
            )
        }

        try {
            val result = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"baidu","site":"example.com","max_results":2}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.host).isEqualTo("www.baidu.com")
            assertThat(capturedRequest?.url?.queryParameter("wd")).isEqualTo("kotlin site:example.com")
            assertThat(capturedRequest?.url?.queryParameter("rn")).isEqualTo("2")
            assertThat(result.content).contains("Baidu Kotlin")
            assertThat(result.content).contains("Baidu snippet")
            assertThat(result.content).contains("https://example.com/baidu")
            assertThat(result.metadata["searchEngine"]).isEqualTo("baidu")
            assertThat(result.metadata["query"]).isEqualTo("kotlin site:example.com")
            assertThat(result.metadata["resultCount"]).isEqualTo(1)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search bing formats html results`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        var capturedRequest: Request? = null
        WebSearchTool.client = fakeClient { request ->
            capturedRequest = request
            textResponse(
                request,
                """
                <html>
                  <body>
                    <li class="b_algo">
                      <h2><a href="https://example.com/bing">Bing Kotlin</a></h2>
                      <p>Bing snippet</p>
                    </li>
                  </body>
                </html>
                """.trimIndent()
            )
        }

        try {
            val result = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"bing","max_results":3}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.host).isEqualTo("www.bing.com")
            assertThat(capturedRequest?.url?.queryParameter("q")).isEqualTo("kotlin")
            assertThat(capturedRequest?.url?.queryParameter("count")).isEqualTo("3")
            assertThat(result.content).contains("Bing Kotlin")
            assertThat(result.content).contains("Bing snippet")
            assertThat(result.content).contains("https://example.com/bing")
            assertThat(result.metadata["searchEngine"]).isEqualTo("bing")
            assertThat(result.metadata["query"]).isEqualTo("kotlin")
            assertThat(result.metadata["resultCount"]).isEqualTo(1)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search google formats html results`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        var capturedRequest: Request? = null
        WebSearchTool.client = fakeClient { request ->
            capturedRequest = request
            textResponse(
                request,
                """
                <html>
                  <body>
                    <div class="g">
                      <a href="https://example.com/google"><h3>Google Kotlin</h3></a>
                      <div class="VwiC3b">Google snippet</div>
                    </div>
                  </body>
                </html>
                """.trimIndent()
            )
        }

        try {
            val result = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"google","max_results":4}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.host).isEqualTo("www.google.com")
            assertThat(capturedRequest?.url?.queryParameter("q")).isEqualTo("kotlin")
            assertThat(capturedRequest?.url?.queryParameter("num")).isEqualTo("4")
            assertThat(result.content).contains("Google Kotlin")
            assertThat(result.content).contains("Google snippet")
            assertThat(result.content).contains("https://example.com/google")
            assertThat(result.metadata["searchEngine"]).isEqualTo("google")
            assertThat(result.metadata["query"]).isEqualTo("kotlin")
            assertThat(result.metadata["resultCount"]).isEqualTo(1)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search handles default engine empty results and engine errors`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        var capturedRequest: Request? = null
        WebSearchTool.client = fakeClient { request ->
            capturedRequest = request
            jsonResponse(request, """{"RelatedTopics":[]}""")
        }

        try {
            val defaulted = WebSearchTool.execute(
                toolCall(
                    WebSearchTool.name,
                    """{"query":"kotlin","search_engine":"unknown","max_results":99}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.host).isEqualTo("api.duckduckgo.com")
            assertThat(defaulted.content).contains("kotlin")
            assertThat(defaulted.metadata["searchEngine"]).isEqualTo("duckduckgo")
            assertThat(defaulted.metadata["hasAbstract"]).isEqualTo(false)
            assertThat(defaulted.metadata["relatedTopicsCount"]).isEqualTo(0)

            WebSearchTool.client = fakeClient { request ->
                if (request.url.host == "api.duckduckgo.com") {
                    jsonResponse(
                        request,
                        """
                        {
                          "RelatedTopics": [
                            {"Name":"Empty","Topics":[{}]}
                          ]
                        }
                        """.trimIndent()
                    )
                } else {
                    textResponse(request, "", code = 503, message = "Service Unavailable")
                }
            }

            val blankRelated = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"blank","search_engine":"duckduckgo"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val baiduError = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"baidu"}"""),
                ToolExecutionContext()
            )
            val bingError = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"bing"}"""),
                ToolExecutionContext()
            )
            val googleError = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"google"}"""),
                ToolExecutionContext()
            )

            assertThat(blankRelated.metadata["relatedTopicsCount"]).isEqualTo(1)
            assertThat(baiduError).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(bingError).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(googleError).isInstanceOf(ToolExecutionResult.Error::class.java)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search html engines return stable empty result metadata`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request ->
            textResponse(request, "<html><body>No matching result markup</body></html>")
        }

        try {
            val baidu = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"none","search_engine":"baidu","max_results":2}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val bing = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"none","search_engine":"bing","max_results":2}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val google = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"none","search_engine":"google","max_results":2}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(baidu.metadata["resultCount"]).isEqualTo(0)
            assertThat(bing.metadata["resultCount"]).isEqualTo(0)
            assertThat(google.metadata["resultCount"]).isEqualTo(0)
            assertThat(baidu.content).contains("none")
            assertThat(bing.content).contains("none")
            assertThat(google.content).contains("none")
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search html engines handle blank snippets and site filters`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request ->
            when (request.url.host) {
                "www.baidu.com" -> textResponse(
                    request,
                    """<h3 class="t"><a href="https://example.com/baidu">Baidu No Snippet</a></h3>"""
                )
                "www.bing.com" -> textResponse(
                    request,
                    """
                    <li class="b_algo">
                      <h2><a href="https://example.com/bing">Bing No Snippet</a></h2>
                      <p></p>
                    </li>
                    """.trimIndent()
                )
                "www.google.com" -> textResponse(
                    request,
                    """
                    <div class="g">
                      <a href="https://example.com/google"><h3>Google No Snippet</h3></a>
                      <div class="VwiC3b"></div>
                    </div>
                    """.trimIndent()
                )
                else -> textResponse(request, "", code = 404, message = "Not Found")
            }
        }

        try {
            val baidu = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"plain","search_engine":"baidu"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val bing = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"bing","site":"example.com"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val google = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"google","site":"example.com"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(baidu.content).contains("Baidu No Snippet")
            assertThat(baidu.metadata["query"]).isEqualTo("plain")
            assertThat(bing.content).contains("Bing No Snippet")
            assertThat(bing.metadata["query"]).isEqualTo("kotlin site:example.com")
            assertThat(google.content).contains("Google No Snippet")
            assertThat(google.metadata["query"]).isEqualTo("kotlin site:example.com")
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search engines report errors when successful response has no body`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request -> noBodyResponse(request) }

        try {
            val baidu = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"baidu"}"""),
                ToolExecutionContext()
            )
            val bing = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"bing"}"""),
                ToolExecutionContext()
            )
            val google = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"google"}"""),
                ToolExecutionContext()
            )
            val duckDuckGo = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"duckduckgo"}"""),
                ToolExecutionContext()
            )

            listOf(baidu, bing, google, duckDuckGo).forEach { result ->
                assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
            }
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search duckduckgo covers related topic edge cases`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request ->
            val query = request.url.queryParameter("q")
            val body = when (query) {
                "no-url" -> """
                    {
                      "RelatedTopics": [
                        {"Text":"Topic without URL"}
                      ]
                    }
                """.trimIndent()
                "limit" -> """
                    {
                      "RelatedTopics": [
                        {"Text":"First topic","FirstURL":"https://example.com/first"},
                        {"Text":"Second topic","FirstURL":"https://example.com/second"}
                      ]
                    }
                """.trimIndent()
                "nested-limit" -> """
                    {
                      "RelatedTopics": [
                        {
                          "Name":"Group",
                          "Topics":[
                            {"Text":"Nested first"},
                            {"Text":"Nested second","FirstURL":"https://example.com/nested-second"}
                          ]
                        }
                      ]
                    }
                """.trimIndent()
                "empty-topic" -> """
                    {
                      "RelatedTopics": [
                        {"Name":"Group without topics"},
                        "not-an-object"
                      ]
                    }
                """.trimIndent()
                "abstract-only" -> """
                    {
                      "AbstractText":"Only summary"
                    }
                """.trimIndent()
                else -> """{"RelatedTopics":[]}"""
            }
            jsonResponse(request, body)
        }

        try {
            val noUrl = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"no-url","max_results":5}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val limited = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"limit","max_results":1}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val nestedLimited = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"nested-limit","max_results":1}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val emptyTopic = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"empty-topic","max_results":5}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val abstractOnly = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"abstract-only"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(noUrl.content).contains("Topic without URL")
            assertThat(limited.content).contains("First topic")
            assertThat(limited.content).doesNotContain("Second topic")
            assertThat(nestedLimited.content).contains("Nested first")
            assertThat(nestedLimited.content).doesNotContain("Nested second")
            assertThat(emptyTopic.content).isNotEmpty()
            assertThat(abstractOnly.content).contains("Only summary")
            assertThat(abstractOnly.metadata["hasAbstract"]).isEqualTo(true)
            assertThat(abstractOnly.metadata["relatedTopicsCount"]).isEqualTo(0)
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search maps duckduckgo http failure and generic exceptions`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { request ->
            jsonResponse(request, """{"error":"down"}""", code = 503, message = "Service Unavailable")
        }

        try {
            val httpFailure = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"duckduckgo"}"""),
                ToolExecutionContext()
            )

            WebSearchTool.client = fakeClient { throw IllegalStateException("broken client") }

            val genericFailure = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin","search_engine":"duckduckgo"}"""),
                ToolExecutionContext()
            )

            assertThat(httpFailure).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(genericFailure).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(genericFailure.error().message).contains("IllegalStateException")
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search maps timeout to tool error`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { throw SocketTimeoutException("slow") }

        try {
            val result = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin"}"""),
                ToolExecutionContext()
            )

            assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(result.error().message).isNotEmpty()
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `web search maps unknown host to network error`(): Unit = runBlocking {
        val originalClient = WebSearchTool.client
        WebSearchTool.client = fakeClient { throw UnknownHostException("offline") }

        try {
            val result = WebSearchTool.execute(
                toolCall(WebSearchTool.name, """{"query":"kotlin"}"""),
                ToolExecutionContext()
            )

            assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(result.error().message).isNotEmpty()
        } finally {
            WebSearchTool.client = originalClient
        }
    }

    @Test
    fun `search tools rethrow cancellation exception`(): Unit = runBlocking {
        val originalGitHubClient = GitHubSearchTool.client
        val originalWebClient = WebSearchTool.client
        val originalFileClient = ReadGitHubFileTool.client
        val cancellingClient = fakeClient { throw CancellationException("cancelled") }
        GitHubSearchTool.client = cancellingClient
        WebSearchTool.client = cancellingClient
        ReadGitHubFileTool.client = cancellingClient

        try {
            assertCancellationRethrown {
                GitHubSearchTool.execute(
                    toolCall(GitHubSearchTool.name, """{"query":"kotlin"}"""),
                    ToolExecutionContext()
                )
            }
            assertCancellationRethrown {
                WebSearchTool.execute(
                    toolCall(WebSearchTool.name, """{"query":"kotlin"}"""),
                    ToolExecutionContext()
                )
            }
            assertCancellationRethrown {
                ReadGitHubFileTool.execute(
                    toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                    ToolExecutionContext()
                )
            }
        } finally {
            GitHubSearchTool.client = originalGitHubClient
            WebSearchTool.client = originalWebClient
            ReadGitHubFileTool.client = originalFileClient
        }
    }

    @Test
    fun `github file validates required parameters without network`(): Unit = runBlocking {
        assertThat(
            ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"","path":"README.md"}"""),
                ToolExecutionContext()
            )
        ).isInstanceOf(ToolExecutionResult.Error::class.java)

        assertThat(
            ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":""}"""),
                ToolExecutionContext()
            )
        ).isInstanceOf(ToolExecutionResult.Error::class.java)

        assertThat(
            ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner","path":"README.md"}"""),
                ToolExecutionContext()
            )
        ).isInstanceOf(ToolExecutionResult.Error::class.java)
    }

    @Test
    fun `github file api result truncates content and exposes metadata`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        var capturedRequest: Request? = null
        ReadGitHubFileTool.client = fakeClient { request ->
            capturedRequest = request
            jsonResponse(
                request,
                """
                {
                  "type": "file",
                  "encoding": "plain",
                  "content": "line1\nline2\nline3",
                  "size": 17,
                  "html_url": "https://github.com/owner/repo/blob/dev/README.md"
                }
                """.trimIndent()
            )
        }

        try {
            val result = ReadGitHubFileTool.execute(
                toolCall(
                    ReadGitHubFileTool.name,
                    """{"repo":"owner/repo","path":"README.md","branch":"dev","max_lines":2}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(capturedRequest?.url?.queryParameter("ref")).isEqualTo("dev")
            assertThat(result.content).contains("line1")
            assertThat(result.content).contains("line2")
            assertThat(result.content).doesNotContain("line3\n```")
            assertThat(result.metadata["repo"]).isEqualTo("owner/repo")
            assertThat(result.metadata["path"]).isEqualTo("README.md")
            assertThat(result.metadata["branch"]).isEqualTo("dev")
            assertThat(result.metadata["totalLines"]).isEqualTo(3)
            assertThat(result.metadata["displayedLines"]).isEqualTo(2)
            assertThat(result.metadata["truncated"]).isEqualTo(true)
            assertThat(result.metadata["method"]).isEqualTo("api")
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file handles api non file and base64 content`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        val encodedContent = Base64.getEncoder().encodeToString("hello\nworld".toByteArray())
        ReadGitHubFileTool.client = fakeClient { request ->
            if (request.url.encodedPath.endsWith("/contents/docs")) {
                jsonResponse(
                    request,
                    """
                    {
                      "type": "dir",
                      "content": "",
                      "size": 0,
                      "html_url": "https://github.com/owner/repo/tree/main/docs"
                    }
                    """.trimIndent()
                )
            } else {
                jsonResponse(
                    request,
                    """
                    {
                      "type": "file",
                      "encoding": "base64",
                      "content": "$encodedContent",
                      "size": 2048,
                      "html_url": "https://github.com/owner/repo/blob/main/README.md"
                    }
                    """.trimIndent()
                )
            }
        }

        try {
            val directory = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"docs"}"""),
                ToolExecutionContext()
            )
            val file = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(directory).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(file.content).contains("hello")
            assertThat(file.content).contains("world")
            assertThat(file.metadata["branch"]).isEqualTo("default")
            assertThat(file.metadata["method"]).isEqualTo("api")
            assertThat(file.metadata["size"]).isEqualTo(2048)
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file formats megabyte api size without truncation`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        ReadGitHubFileTool.client = fakeClient { request ->
            jsonResponse(
                request,
                """
                {
                  "type": "file",
                  "encoding": "plain",
                  "content": "single-line",
                  "size": 2097152,
                  "html_url": "https://github.com/owner/repo/blob/main/LARGE.md"
                }
                """.trimIndent()
            )
        }

        try {
            val result = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"LARGE.md","max_lines":50}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(result.content).contains("2 MB")
            assertThat(result.metadata["size"]).isEqualTo(2097152)
            assertThat(result.metadata["truncated"]).isEqualTo(false)
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file falls back to raw endpoint when api misses`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        val seenHosts = mutableListOf<String>()
        ReadGitHubFileTool.client = fakeClient { request ->
            seenHosts += request.url.host
            when (request.url.host) {
                "api.github.com" -> jsonResponse(request, "{}", code = 404, message = "Not Found")
                "raw.githubusercontent.com" -> textResponse(request, "raw-line-1\nraw-line-2")
                else -> jsonResponse(request, "{}", code = 500, message = "Unexpected")
            }
        }

        try {
            val result = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(seenHosts).containsExactly("api.github.com", "raw.githubusercontent.com").inOrder()
            assertThat(result.content).contains("raw-line-1")
            assertThat(result.metadata["branch"]).isEqualTo("main")
            assertThat(result.metadata["method"]).isEqualTo("raw")
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file falls back when api response body is missing`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        val seenHosts = mutableListOf<String>()
        ReadGitHubFileTool.client = fakeClient { request ->
            seenHosts += request.url.host
            when (request.url.host) {
                "api.github.com" -> noBodyResponse(request)
                "raw.githubusercontent.com" -> textResponse(request, "raw-after-empty-api")
                else -> textResponse(request, "", code = 500, message = "Unexpected")
            }
        }

        try {
            val result = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(seenHosts).containsExactly("api.github.com", "raw.githubusercontent.com").inOrder()
            assertThat(result.content).contains("raw-after-empty-api")
            assertThat(result.metadata["method"]).isEqualTo("raw")
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file falls back after blank or invalid api content and truncates raw branch`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        val seenPaths = mutableListOf<String>()
        ReadGitHubFileTool.client = fakeClient { request ->
            seenPaths += request.url.encodedPath
            when {
                request.url.host == "api.github.com" && request.url.encodedPath.endsWith("/contents/blank.md") -> jsonResponse(
                    request,
                    """
                    {
                      "type": "file",
                      "encoding": "plain",
                      "content": "",
                      "size": 0,
                      "html_url": "https://github.com/owner/repo/blob/main/blank.md"
                    }
                    """.trimIndent()
                )
                request.url.host == "api.github.com" && request.url.encodedPath.endsWith("/contents/invalid.md") -> jsonResponse(
                    request,
                    """
                    {
                      "type": "file",
                      "encoding": "base64",
                      "content": "%%%not-base64%%%",
                      "size": 12,
                      "html_url": "https://github.com/owner/repo/blob/dev/invalid.md"
                    }
                    """.trimIndent()
                )
                request.url.host == "raw.githubusercontent.com" && request.url.encodedPath.endsWith("/main/blank.md") ->
                    textResponse(request, "blank-raw")
                request.url.host == "raw.githubusercontent.com" && request.url.encodedPath.endsWith("/dev/invalid.md") ->
                    textResponse(request, "raw-1\nraw-2\nraw-3")
                else -> textResponse(request, "", code = 404, message = "Not Found")
            }
        }

        try {
            val blankFallback = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"blank.md"}"""),
                ToolExecutionContext()
            ).expectSuccess()
            val invalidFallback = ReadGitHubFileTool.execute(
                toolCall(
                    ReadGitHubFileTool.name,
                    """{"repo":"owner/repo","path":"invalid.md","branch":"dev","max_lines":2}"""
                ),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(seenPaths).containsAtLeast(
                "/repos/owner/repo/contents/blank.md",
                "/owner/repo/main/blank.md",
                "/repos/owner/repo/contents/invalid.md",
                "/owner/repo/dev/invalid.md"
            )
            assertThat(blankFallback.content).contains("blank-raw")
            assertThat(blankFallback.metadata["method"]).isEqualTo("raw")
            assertThat(invalidFallback.content).contains("raw-1")
            assertThat(invalidFallback.content).contains("raw-2")
            assertThat(invalidFallback.content).doesNotContain("raw-3\n```")
            assertThat(invalidFallback.metadata["branch"]).isEqualTo("dev")
            assertThat(invalidFallback.metadata["displayedLines"]).isEqualTo(2)
            assertThat(invalidFallback.metadata["truncated"]).isEqualTo(true)
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file retries master raw and reports final read failure`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        val seenPaths = mutableListOf<String>()
        ReadGitHubFileTool.client = fakeClient { request ->
            seenPaths += request.url.encodedPath
            when {
                request.url.host == "api.github.com" -> jsonResponse(request, "{}", code = 404, message = "Not Found")
                request.url.encodedPath.contains("/main/") -> textResponse(
                    request,
                    "",
                    code = 404,
                    message = "Not Found"
                )
                request.url.encodedPath.contains("/master/") -> textResponse(request, "master-line")
                else -> textResponse(request, "", code = 500, message = "Unexpected")
            }
        }

        try {
            val master = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            ).expectSuccess()

            assertThat(seenPaths).containsAtLeast(
                "/repos/owner/repo/contents/README.md",
                "/owner/repo/main/README.md",
                "/owner/repo/master/README.md"
            ).inOrder()
            assertThat(master.content).contains("master-line")
            assertThat(master.metadata["branch"]).isEqualTo("master")

            ReadGitHubFileTool.client = fakeClient { request ->
                if (request.url.host == "api.github.com") {
                    jsonResponse(request, "{}", code = 404, message = "Not Found")
                } else {
                    textResponse(request, "", code = 404, message = "Not Found")
                }
            }

            val missing = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"MISSING.md"}"""),
                ToolExecutionContext()
            )

            assertThat(missing).isInstanceOf(ToolExecutionResult.Error::class.java)
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github file rethrows cancellation during raw fallback`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        ReadGitHubFileTool.client = fakeClient { request ->
            if (request.url.host == "api.github.com") {
                jsonResponse(request, "{}", code = 404, message = "Not Found")
            } else {
                throw CancellationException("cancelled")
            }
        }

        try {
            assertCancellationRethrown {
                ReadGitHubFileTool.execute(
                    toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                    ToolExecutionContext()
                )
            }
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    @Test
    fun `github tools map network timeout to tool errors`(): Unit = runBlocking {
        val originalSearchClient = GitHubSearchTool.client
        val originalFileClient = ReadGitHubFileTool.client
        val timeoutClient = fakeClient { throw SocketTimeoutException("slow") }
        GitHubSearchTool.client = timeoutClient
        ReadGitHubFileTool.client = timeoutClient

        try {
            val search = GitHubSearchTool.execute(
                toolCall(GitHubSearchTool.name, """{"query":"kotlin"}"""),
                ToolExecutionContext()
            )
            val file = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            )

            assertThat(search).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(file).isInstanceOf(ToolExecutionResult.Error::class.java)
        } finally {
            GitHubSearchTool.client = originalSearchClient
            ReadGitHubFileTool.client = originalFileClient
        }
    }

    @Test
    fun `github file maps unknown host to network error`(): Unit = runBlocking {
        val originalClient = ReadGitHubFileTool.client
        ReadGitHubFileTool.client = fakeClient { throw UnknownHostException("offline") }

        try {
            val result = ReadGitHubFileTool.execute(
                toolCall(ReadGitHubFileTool.name, """{"repo":"owner/repo","path":"README.md"}"""),
                ToolExecutionContext()
            )

            assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(result.error().message).isNotEmpty()
        } finally {
            ReadGitHubFileTool.client = originalClient
        }
    }

    private suspend fun assertCancellationRethrown(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertThat(e).hasMessageThat().isEqualTo("cancelled")
        }
    }

    private fun ToolExecutionResult.expectSuccess(): ToolExecutionResult.Success {
        assertWithMessage(toString()).that(this).isInstanceOf(ToolExecutionResult.Success::class.java)
        return this as ToolExecutionResult.Success
    }

    private fun resetAppStrings() {
        val field = AppStrings::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(AppStrings, null)
    }
    private fun fakeClient(handler: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> handler(chain.request()) }
        .build()

    private fun jsonResponse(
        request: Request,
        body: String,
        code: Int = 200,
        message: String = "OK"
    ): Response = response(request, body, "application/json", code, message)

    private fun textResponse(
        request: Request,
        body: String,
        code: Int = 200,
        message: String = "OK"
    ): Response = response(request, body, "text/plain", code, message)

    private fun noBodyResponse(
        request: Request,
        code: Int = 200,
        message: String = "OK"
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .build()

    private fun response(
        request: Request,
        body: String,
        contentType: String,
        code: Int = 200,
        message: String = "OK"
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body(body.toResponseBody(contentType.toMediaType()))
        .build()
}
