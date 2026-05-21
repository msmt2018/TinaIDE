package com.wuxianggujun.tinaide.ai.tools.search

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.i18n.strOr
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * GitHub 代码搜索工具
 * 在 GitHub 上搜索代码、仓库、问题等
 */
object GitHubSearchTool : AiTool {
    override val name = "github_search"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_github_search,
            "Search GitHub for code, repositories, issues, or users. Useful for finding code examples, libraries, solutions to problems, or checking if similar projects exist. Returns repository names, descriptions, stars, and relevant code snippets."
        )
    override val category = ToolCategory.WEB
    override val isDangerous = false

    internal var client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_search_query_e_g_language_cpp_cmake_user,
                                "Search query (e.g., 'language:cpp cmake', 'user:torvalds linux', 'repo:llvm/llvm-project')"
                            )
                        )
                    }
                )
                put(
                    "search_type",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_type_of_search_code_repositories_issues_or_users,
                                "Type of search: 'code', 'repositories', 'issues', or 'users' (default: 'repositories')"
                            )
                        )
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("code"))
                                add(JsonPrimitive("repositories"))
                                add(JsonPrimitive("issues"))
                                add(JsonPrimitive("users"))
                            }
                        )
                        put("default", "repositories")
                    }
                )
                put(
                    "language",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_filter_by_programming_language_e_g_cpp_c,
                                "Filter by programming language (e.g., 'C++', 'C', 'Python', 'Java')"
                            )
                        )
                    }
                )
                put(
                    "sort",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_sort_results_by_stars_forks_updated_or_best,
                                "Sort results by: 'stars', 'forks', 'updated', or 'best-match' (default: 'best-match')"
                            )
                        )
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("stars"))
                                add(JsonPrimitive("forks"))
                                add(JsonPrimitive("updated"))
                                add(JsonPrimitive("best-match"))
                            }
                        )
                        put("default", "best-match")
                    }
                )
                put(
                    "max_results",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_maximum_number_of_results_to_return_default_10_2,
                                "Maximum number of results to return (default: 10, max: 30)"
                            )
                        )
                        put("default", 10)
                        put("minimum", 1)
                        put("maximum", 30)
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("query"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val query = ToolParameterParser.getStringParameter(args, "query")
        val searchType = ToolParameterParser.getStringParameter(args, "search_type", "repositories")
        val language = args["language"]
        val sort = ToolParameterParser.getStringParameter(args, "sort", "best-match")
        val maxResults = ToolParameterParser.getIntParameter(args, "max_results", 10).coerceIn(1, 30)

        if (query.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_search_error_query_required.str())
        }

        return withContext(Dispatchers.IO) {
            try {
                // 构建搜索查询
                val fullQuery = buildString {
                    append(query)
                    if (language != null && !query.contains("language:")) {
                        append(" language:$language")
                    }
                }

                val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
                val sortParam = if (sort != "best-match") "&sort=$sort&order=desc" else ""
                val url = "https://api.github.com/search/$searchType?q=$encodedQuery&per_page=$maxResults$sortParam"

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "TinaIDE-AI-Assistant")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    val errorMsg = when (response.code) {
                        403 -> Strings.ai_search_github_rate_limit.str()
                        401 -> Strings.ai_search_github_auth_failed.str()
                        422 -> Strings.ai_search_github_invalid_query.str()
                        else -> Strings.ai_search_github_request_failed.str(response.code, response.message)
                    }
                    return@withContext ToolExecutionResult.Error(errorMsg)
                }

                val json = JSONObject(responseBody)
                val totalCount = json.optInt("total_count", 0)
                val items = json.optJSONArray("items")

                if (items == null || items.length() == 0) {
                    return@withContext ToolExecutionResult.Success(
                        Strings.ai_search_results_none_for_query.str(fullQuery)
                    )
                }

                val content = buildString {
                    appendLine(Strings.ai_search_github_results_title.str(searchType))
                    appendLine(Strings.ai_search_results_query.str(fullQuery))
                    appendLine(Strings.ai_search_github_results_count.str(totalCount, items.length()))
                    appendLine()

                    when (searchType) {
                        "repositories" -> formatRepositories(items)
                        "code" -> formatCodeResults(items)
                        "issues" -> formatIssues(items)
                        "users" -> formatUsers(items)
                        else -> appendLine(Strings.ai_search_error_unknown_type.str(searchType))
                    }
                }

                val metadata = mapOf(
                    "searchType" to searchType,
                    "totalCount" to totalCount,
                    "resultCount" to items.length(),
                    "query" to fullQuery
                )

                ToolExecutionResult.Success(content, metadata)
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                ToolExecutionResult.Error(Strings.ai_search_error_network_unreachable.str())
            } catch (e: java.net.SocketTimeoutException) {
                ToolExecutionResult.Error(Strings.ai_search_error_timeout.str())
            } catch (e: Exception) {
                ToolExecutionResult.Error(
                    Strings.ai_search_error_failed_with_detail.str(
                        "GitHub",
                        "${e.javaClass.simpleName} - ${e.message}"
                    )
                )
            }
        }
    }

    private fun StringBuilder.formatRepositories(items: org.json.JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("full_name", Strings.ai_search_unknown_placeholder.str())
            val description = item.optString("description", Strings.ai_search_no_description.str())
            val stars = item.optInt("stargazers_count", 0)
            val forks = item.optInt("forks_count", 0)
            val language = item.optString("language", Strings.ai_search_unknown_placeholder.str())
            val url = item.optString("html_url", "")
            val updated = item.optString("updated_at", "")

            appendLine("${i + 1}. $name ⭐ $stars 🍴 $forks")
            appendLine(Strings.ai_search_label_language.strOr(null, language))
            appendLine(Strings.ai_search_label_description.strOr(null, description))
            appendLine(Strings.ai_search_label_url.strOr(null, url))
            appendLine(Strings.ai_search_label_last_updated.strOr(null, updated.take(10)))
            appendLine()
        }
    }

    private fun StringBuilder.formatCodeResults(items: org.json.JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("name", Strings.ai_search_unknown_placeholder.str())
            val path = item.optString("path", "")
            val repo = item.optJSONObject("repository")
            val repoName = repo?.optString("full_name", Strings.ai_search_unknown_placeholder.str())
                ?: Strings.ai_search_unknown_placeholder.str()
            val url = item.optString("html_url", "")

            appendLine("${i + 1}. $name")
            appendLine(Strings.ai_search_label_repository.strOr(null, repoName))
            appendLine(Strings.ai_search_label_path.strOr(null, path))
            appendLine(Strings.ai_search_label_url.strOr(null, url))
            appendLine()
        }
    }

    private fun StringBuilder.formatIssues(items: org.json.JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.optString("title", Strings.ai_search_unknown_placeholder.str())
            val state = item.optString("state", Strings.ai_search_unknown_placeholder.str())
            val number = item.optInt("number", 0)
            val url = item.optString("html_url", "")
            val user = item.optJSONObject("user")?.optString("login", Strings.ai_search_unknown_placeholder.str())
                ?: Strings.ai_search_unknown_placeholder.str()
            val created = item.optString("created_at", "")

            appendLine("${i + 1}. #$number - $title [$state]")
            appendLine(Strings.ai_search_label_author.strOr(null, user))
            appendLine(Strings.ai_search_label_created.strOr(null, created.take(10)))
            appendLine(Strings.ai_search_label_url.strOr(null, url))
            appendLine()
        }
    }

    private fun StringBuilder.formatUsers(items: org.json.JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val login = item.optString("login", Strings.ai_search_unknown_placeholder.str())
            val type = item.optString("type", Strings.ai_search_default_user_type.str())
            val url = item.optString("html_url", "")

            appendLine("${i + 1}. $login ($type)")
            appendLine(Strings.ai_search_label_url.strOr(null, url))
            appendLine()
        }
    }
}

/**
 * 网络搜索工具
 * 使用搜索引擎搜索网络内容，支持百度、必应、Google 等
 */
object WebSearchTool : AiTool {
    override val name = "web_search"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_web_search,
            "Search the web for information, documentation, tutorials, or solutions to problems using various search engines (Baidu, Bing, Google, DuckDuckGo). Useful for finding up-to-date information, API documentation, error solutions, or learning resources. Returns search results with titles, snippets, and URLs."
        )
    override val category = ToolCategory.WEB
    override val isDangerous = false

    internal var client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_search_query_e_g_cmake_tutorial_clang_format,
                                "Search query (e.g., 'cmake tutorial', 'clang-format configuration', 'C++ best practices')"
                            )
                        )
                    }
                )
                put(
                    "search_engine",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_search_engine_to_use_baidu_bing_google_or,
                                "Search engine to use: 'baidu', 'bing', 'google', or 'duckduckgo' (default: 'duckduckgo')"
                            )
                        )
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("baidu"))
                                add(JsonPrimitive("bing"))
                                add(JsonPrimitive("google"))
                                add(JsonPrimitive("duckduckgo"))
                            }
                        )
                        put("default", "duckduckgo")
                    }
                )
                put(
                    "max_results",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_maximum_number_of_results_to_return_default_10,
                                "Maximum number of results to return (default: 10, max: 20)"
                            )
                        )
                        put("default", 10)
                        put("minimum", 1)
                        put("maximum", 20)
                    }
                )
                put(
                    "site",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_limit_search_to_specific_site_e_g_stackoverflow,
                                "Limit search to specific site (e.g., 'stackoverflow.com', 'cppreference.com')"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("query"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val query = ToolParameterParser.getStringParameter(args, "query")
        val searchEngine = ToolParameterParser.getStringParameter(args, "search_engine", "duckduckgo")
        val maxResults = ToolParameterParser.getIntParameter(args, "max_results", 10).coerceIn(1, 20)
        val site = args["site"]

        if (query.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_search_error_query_required.str())
        }

        return withContext(Dispatchers.IO) {
            try {
                when (searchEngine.lowercase()) {
                    "baidu" -> searchBaidu(query, site, maxResults)
                    "bing" -> searchBing(query, site, maxResults)
                    "google" -> searchGoogle(query, site, maxResults)
                    "duckduckgo" -> searchDuckDuckGo(query, site, maxResults)
                    else -> searchDuckDuckGo(query, site, maxResults)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                ToolExecutionResult.Error(Strings.ai_search_error_network_unreachable.str())
            } catch (e: java.net.SocketTimeoutException) {
                ToolExecutionResult.Error(Strings.ai_search_error_timeout.str())
            } catch (e: Exception) {
                ToolExecutionResult.Error(
                    Strings.ai_search_error_failed_with_detail.str(
                        Strings.ai_search_web_label.str(),
                        "${e.javaClass.simpleName} - ${e.message}"
                    )
                )
            }
        }
    }

    private fun searchBaidu(query: String, site: String?, maxResults: Int): ToolExecutionResult {
        // 百度搜索 - 使用简单的 HTML 抓取方式
        val fullQuery = if (site != null) "$query site:$site" else query
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
        val url = "https://www.baidu.com/s?wd=$encodedQuery&rn=$maxResults"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string()

        if (!response.isSuccessful || html == null) {
            return ToolExecutionResult.Error(
                Strings.ai_search_engine_request_failed.str(
                    Strings.ai_search_engine_baidu.str(),
                    response.code,
                    response.message
                )
            )
        }

        // 简单的 HTML 解析提取搜索结果
        val results = parseBaiduResults(html, maxResults)

        val content = buildString {
            appendLine(Strings.ai_search_results_baidu_title.str())
            appendLine(Strings.ai_search_results_query.str(fullQuery))
            appendLine(Strings.ai_search_results_count.str(results.size))
            appendLine()

            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}")
                if (result.snippet.isNotBlank()) {
                    appendLine("   ${result.snippet}")
                }
                appendLine(Strings.ai_search_label_url.strOr(null, result.url))
                appendLine()
            }

            if (results.isEmpty()) {
                appendLine(Strings.ai_search_results_empty_title.str())
                appendLine(Strings.ai_search_results_tip_diff_keyword.str())
                appendLine(Strings.ai_search_results_tip_specific_keyword.str())
                appendLine(Strings.ai_search_results_tip_site.str())
            }
        }

        val metadata = mapOf(
            "searchEngine" to "baidu",
            "query" to fullQuery,
            "resultCount" to results.size
        )

        return ToolExecutionResult.Success(content, metadata)
    }

    private fun searchBing(query: String, site: String?, maxResults: Int): ToolExecutionResult {
        // 必应搜索 - 使用 Bing Web Search API 或 HTML 抓取
        val fullQuery = if (site != null) "$query site:$site" else query
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
        val url = "https://www.bing.com/search?q=$encodedQuery&count=$maxResults"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string()

        if (!response.isSuccessful || html == null) {
            return ToolExecutionResult.Error(
                Strings.ai_search_engine_request_failed.str(
                    Strings.ai_search_engine_bing.str(),
                    response.code,
                    response.message
                )
            )
        }

        // 简单的 HTML 解析提取搜索结果
        val results = parseBingResults(html, maxResults)

        val content = buildString {
            appendLine(Strings.ai_search_results_bing_title.str())
            appendLine(Strings.ai_search_results_query.str(fullQuery))
            appendLine(Strings.ai_search_results_count.str(results.size))
            appendLine()

            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}")
                if (result.snippet.isNotBlank()) {
                    appendLine("   ${result.snippet}")
                }
                appendLine(Strings.ai_search_label_url.strOr(null, result.url))
                appendLine()
            }

            if (results.isEmpty()) {
                appendLine(Strings.ai_search_results_empty_title.str())
                appendLine(Strings.ai_search_results_tip_diff_keyword.str())
                appendLine(Strings.ai_search_results_tip_specific_keyword.str())
                appendLine(Strings.ai_search_results_tip_site.str())
            }
        }

        val metadata = mapOf(
            "searchEngine" to "bing",
            "query" to fullQuery,
            "resultCount" to results.size
        )

        return ToolExecutionResult.Success(content, metadata)
    }

    private fun searchGoogle(query: String, site: String?, maxResults: Int): ToolExecutionResult {
        // Google 搜索 - 使用 Custom Search API 或 HTML 抓取
        val fullQuery = if (site != null) "$query site:$site" else query
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
        val url = "https://www.google.com/search?q=$encodedQuery&num=$maxResults"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string()

        if (!response.isSuccessful || html == null) {
            return ToolExecutionResult.Error(
                Strings.ai_search_engine_request_failed.str(
                    Strings.ai_search_engine_google.str(),
                    response.code,
                    response.message
                )
            )
        }

        // 简单的 HTML 解析提取搜索结果
        val results = parseGoogleResults(html, maxResults)

        val content = buildString {
            appendLine(Strings.ai_search_results_google_title.str())
            appendLine(Strings.ai_search_results_query.str(fullQuery))
            appendLine(Strings.ai_search_results_count.str(results.size))
            appendLine()

            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}")
                if (result.snippet.isNotBlank()) {
                    appendLine("   ${result.snippet}")
                }
                appendLine(Strings.ai_search_label_url.strOr(null, result.url))
                appendLine()
            }

            if (results.isEmpty()) {
                appendLine(Strings.ai_search_results_empty_title.str())
                appendLine(Strings.ai_search_results_tip_diff_keyword.str())
                appendLine(Strings.ai_search_results_tip_specific_keyword.str())
                appendLine(Strings.ai_search_results_tip_site.str())
            }
        }

        val metadata = mapOf(
            "searchEngine" to "google",
            "query" to fullQuery,
            "resultCount" to results.size
        )

        return ToolExecutionResult.Success(content, metadata)
    }

    private fun searchDuckDuckGo(query: String, site: String?, maxResults: Int): ToolExecutionResult {
        // 构建搜索查询
        val fullQuery = if (site != null) {
            "$query site:$site"
        } else {
            query
        }

        // 使用 DuckDuckGo Instant Answer API (免费，无需 API key)
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TinaIDE-AI-Assistant")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
            return ToolExecutionResult.Error(
                Strings.ai_search_engine_request_failed.str(
                    Strings.ai_search_engine_duckduckgo.str(),
                    response.code,
                    response.message
                )
            )
        }

        val json = JSONObject(responseBody)
        val abstract = json.optString("Abstract", "")
        val abstractText = json.optString("AbstractText", "")
        val abstractSource = json.optString("AbstractSource", "")
        val abstractUrl = json.optString("AbstractURL", "")
        val relatedTopics = json.optJSONArray("RelatedTopics")

        val content = buildString {
            appendLine(Strings.ai_search_results_duckduckgo_title.str())
            appendLine(Strings.ai_search_results_query.str(fullQuery))
            appendLine()

            // 主要答案
            if (abstractText.isNotBlank()) {
                appendLine(Strings.ai_search_results_summary_title.str())
                appendLine(abstractText)
                if (abstractSource.isNotBlank()) {
                    appendLine(Strings.ai_search_label_source.strOr(null, abstractSource))
                }
                if (abstractUrl.isNotBlank()) {
                    appendLine(Strings.ai_search_label_url.strOr(null, abstractUrl))
                }
                appendLine()
            }

            // 相关主题
            if (relatedTopics != null && relatedTopics.length() > 0) {
                appendLine(Strings.ai_search_results_related_title.str())
                appendLine()

                var count = 0
                for (i in 0 until relatedTopics.length()) {
                    if (count >= maxResults) break

                    val topic = relatedTopics.optJSONObject(i)
                    if (topic != null) {
                        val text = topic.optString("Text", "")
                        val firstUrl = topic.optString("FirstURL", "")

                        if (text.isNotBlank()) {
                            count++
                            appendLine("$count. $text")
                            if (firstUrl.isNotBlank()) {
                                appendLine(Strings.ai_search_label_url.strOr(null, firstUrl))
                            }
                            appendLine()
                        } else {
                            val topics = topic.optJSONArray("Topics")
                            if (topics != null) {
                                for (j in 0 until topics.length()) {
                                    if (count >= maxResults) break

                                    val subTopic = topics.optJSONObject(j)
                                    val text = subTopic?.optString("Text", "") ?: ""
                                    val firstUrl = subTopic?.optString("FirstURL", "") ?: ""

                                    if (text.isNotBlank()) {
                                        count++
                                        appendLine("$count. $text")
                                        if (firstUrl.isNotBlank()) {
                                            appendLine(Strings.ai_search_label_url.strOr(null, firstUrl))
                                        }
                                        appendLine()
                                    }
                                }
                            }
                        }
                    }
                }

                if (count == 0) {
                    appendLine(Strings.ai_search_results_related_empty.str())
                }
            } else if (abstractText.isBlank()) {
                appendLine(Strings.ai_search_results_none_for_query.str(fullQuery))
                appendLine()
                appendLine(Strings.ai_search_results_tip_title.str())
                appendLine(Strings.ai_search_results_tip_diff_keyword.str())
                appendLine(Strings.ai_search_results_tip_specific_keyword.str())
                appendLine(Strings.ai_search_results_tip_site.str())
            }
        }

        val metadata = mapOf(
            "searchEngine" to "duckduckgo",
            "query" to fullQuery,
            "hasAbstract" to abstractText.isNotBlank(),
            "relatedTopicsCount" to (relatedTopics?.length() ?: 0)
        )

        return ToolExecutionResult.Success(content, metadata)
    }

    // 搜索结果数据类
    private data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String
    )

    // 解析百度搜索结果
    private fun parseBaiduResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // 简单的正则表达式提取（实际应用中应使用 HTML 解析库）
        // 这里提供基本实现，可能需要根据百度 HTML 结构调整
        val titlePattern = """<h3[^>]*class="[^"]*t[^"]*"[^>]*><a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>""".toRegex()
        val snippetPattern = """<div[^>]*class="[^"]*c-abstract[^"]*"[^>]*>([^<]*)</div>""".toRegex()

        val titleMatches = titlePattern.findAll(html).take(maxResults)
        val snippetMatches = snippetPattern.findAll(html).take(maxResults).toList()

        titleMatches.forEachIndexed { index, match ->
            val url = match.groupValues.getOrNull(1) ?: ""
            val title = match.groupValues.getOrNull(2) ?: ""
            val snippet = snippetMatches.getOrNull(index)?.groupValues?.getOrNull(1) ?: ""

            if (title.isNotBlank() && url.isNotBlank()) {
                results.add(
                    SearchResult(
                        title = title.trim(),
                        url = url.trim(),
                        snippet = snippet.trim()
                    )
                )
            }
        }

        return results
    }

    // 解析必应搜索结果
    private fun parseBingResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // 简单的正则表达式提取
        val resultPattern = """<li[^>]*class="[^"]*b_algo[^"]*"[^>]*>.*?<h2><a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>.*?<p>([^<]*)</p>""".toRegex(RegexOption.DOT_MATCHES_ALL)

        val matches = resultPattern.findAll(html).take(maxResults)

        matches.forEach { match ->
            val url = match.groupValues.getOrNull(1) ?: ""
            val title = match.groupValues.getOrNull(2) ?: ""
            val snippet = match.groupValues.getOrNull(3) ?: ""

            if (title.isNotBlank() && url.isNotBlank()) {
                results.add(
                    SearchResult(
                        title = title.trim(),
                        url = url.trim(),
                        snippet = snippet.trim()
                    )
                )
            }
        }

        return results
    }

    // 解析 Google 搜索结果
    private fun parseGoogleResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // 简单的正则表达式提取
        val resultPattern = """<div[^>]*class="[^"]*g[^"]*"[^>]*>.*?<a[^>]*href="([^"]*)"[^>]*><h3[^>]*>([^<]*)</h3>.*?<div[^>]*class="[^"]*VwiC3b[^"]*"[^>]*>([^<]*)</div>""".toRegex(RegexOption.DOT_MATCHES_ALL)

        val matches = resultPattern.findAll(html).take(maxResults)

        matches.forEach { match ->
            val url = match.groupValues.getOrNull(1) ?: ""
            val title = match.groupValues.getOrNull(2) ?: ""
            val snippet = match.groupValues.getOrNull(3) ?: ""

            if (title.isNotBlank() && url.isNotBlank()) {
                results.add(
                    SearchResult(
                        title = title.trim(),
                        url = url.trim(),
                        snippet = snippet.trim()
                    )
                )
            }
        }

        return results
    }
}

/**
 * 读取 GitHub 文件内容工具
 * 从 GitHub 仓库读取文件内容
 */
object ReadGitHubFileTool : AiTool {
    override val name = "read_github_file"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_read_github_file,
            "Read file content from a GitHub repository. Useful for examining code examples, reading documentation, or studying implementations from open source projects. Supports reading from specific branches or commits."
        )
    override val category = ToolCategory.WEB
    override val isDangerous = false

    internal var client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "repo",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_repository_in_format_owner_repo_e_g_torvalds,
                                "Repository in format 'owner/repo' (e.g., 'torvalds/linux', 'llvm/llvm-project')"
                            )
                        )
                    }
                )
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_file_path_in_the_repository_e_g_src,
                                "File path in the repository (e.g., 'src/main.cpp', 'README.md')"
                            )
                        )
                    }
                )
                put(
                    "branch",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_branch_or_commit_sha_default_repository_s_default,
                                "Branch or commit SHA (default: repository's default branch)"
                            )
                        )
                    }
                )
                put(
                    "max_lines",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_maximum_number_of_lines_to_return_default_500,
                                "Maximum number of lines to return (default: 500, max: 2000)"
                            )
                        )
                        put("default", 500)
                        put("minimum", 1)
                        put("maximum", 2000)
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("repo"))
                add(JsonPrimitive("path"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val repo = ToolParameterParser.getStringParameter(args, "repo")
        val path = ToolParameterParser.getStringParameter(args, "path")
        val branch = args["branch"]
        val maxLines = ToolParameterParser.getIntParameter(args, "max_lines", 500).coerceIn(1, 2000)

        if (repo.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_search_github_repo_required.str())
        }

        if (path.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_search_github_path_required.str())
        }

        // 验证仓库格式
        if (!repo.contains("/")) {
            return ToolExecutionResult.Error(Strings.ai_search_github_repo_invalid_format.str())
        }

        return withContext(Dispatchers.IO) {
            try {
                // 首先尝试使用 GitHub API
                val apiResult = tryGitHubAPI(repo, path, branch, maxLines)
                if (apiResult != null) {
                    return@withContext apiResult
                }

                // 如果 API 失败，尝试使用 raw.githubusercontent.com
                val rawResult = tryGitHubRaw(repo, path, branch, maxLines)
                if (rawResult != null) {
                    return@withContext rawResult
                }

                // 都失败了
                ToolExecutionResult.Error(Strings.ai_search_github_file_read_failed.str())
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                ToolExecutionResult.Error(Strings.ai_search_error_network_unreachable.str())
            } catch (e: java.net.SocketTimeoutException) {
                ToolExecutionResult.Error(Strings.ai_search_error_timeout.str())
            } catch (e: Exception) {
                ToolExecutionResult.Error(
                    Strings.ai_search_error_failed_with_detail.str(
                        "GitHub",
                        "${e.javaClass.simpleName} - ${e.message}"
                    )
                )
            }
        }
    }

    private fun tryGitHubAPI(repo: String, path: String, branch: String?, maxLines: Int): ToolExecutionResult? {
        return try {
            // 构建 GitHub API URL
            val ref = if (branch != null) "?ref=$branch" else ""
            val url = "https://api.github.com/repos/$repo/contents/$path$ref"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TinaIDE-AI-Assistant")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                // API 失败，返回 null 以尝试其他方法
                return null
            }

            val json = JSONObject(responseBody)
            val type = json.optString("type", "")

            if (type != "file") {
                return ToolExecutionResult.Error(Strings.ai_search_github_path_not_file.str(path, type))
            }

            val encoding = json.optString("encoding", "")
            val content = json.optString("content", "")
            val size = json.optInt("size", 0)
            val htmlUrl = json.optString("html_url", "")

            if (content.isBlank()) {
                return null
            }

            // 解码 Base64 内容
            val decodedContent = if (encoding == "base64") {
                try {
                    String(android.util.Base64.decode(content.replace("\n", ""), android.util.Base64.DEFAULT))
                } catch (e: Exception) {
                    return null
                }
            } else {
                content
            }

            // 限制行数
            val lines = decodedContent.lines()
            val truncated = lines.size > maxLines
            val displayLines = if (truncated) lines.take(maxLines) else lines
            val displayContent = displayLines.joinToString("\n")

            val result = buildString {
                appendLine(Strings.ai_search_github_file_title.str())
                appendLine(Strings.ai_search_label_repository.strOr(null, repo))
                appendLine(Strings.ai_search_label_path.strOr(null, path))
                if (branch != null) {
                    appendLine(Strings.ai_search_branch_commit_label.str(branch))
                }
                appendLine(Strings.ai_search_label_size.strOr(null, formatFileSize(size.toLong())))
                appendLine(
                    Strings.ai_search_label_lines.strOr(
                        null,
                        if (truncated) {
                            Strings.ai_search_lines_truncated.str(lines.size, maxLines)
                        } else {
                            Strings.ai_search_lines_total.str(lines.size)
                        }
                    )
                )
                appendLine(Strings.ai_search_label_url.strOr(null, htmlUrl))
                appendLine()
                appendLine(Strings.ai_search_label_content.str())
                appendLine("```")
                appendLine(displayContent)
                appendLine("```")

                if (truncated) {
                    appendLine()
                    appendLine(Strings.ai_search_content_truncated_note.str(lines.size))
                    appendLine(Strings.ai_search_content_truncated_hint.str())
                    appendLine(htmlUrl)
                }
            }

            val metadata = mapOf(
                "repo" to repo,
                "path" to path,
                "branch" to (branch ?: "default"),
                "size" to size,
                "totalLines" to lines.size,
                "displayedLines" to displayLines.size,
                "truncated" to truncated,
                "url" to htmlUrl,
                "method" to "api"
            )

            ToolExecutionResult.Success(result, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun tryGitHubRaw(repo: String, path: String, branch: String?, maxLines: Int): ToolExecutionResult? {
        return try {
            // 使用 raw.githubusercontent.com
            val branchName = branch ?: "main"
            val url = "https://raw.githubusercontent.com/$repo/$branchName/$path"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TinaIDE-AI-Assistant")
                .build()

            val response = client.newCall(request).execute()
            val content = response.body?.string()

            if (!response.isSuccessful || content == null) {
                // 尝试 master 分支
                if (branchName == "main") {
                    return tryGitHubRaw(repo, path, "master", maxLines)
                }
                return null
            }

            // 限制行数
            val lines = content.lines()
            val truncated = lines.size > maxLines
            val displayLines = if (truncated) lines.take(maxLines) else lines
            val displayContent = displayLines.joinToString("\n")

            val htmlUrl = "https://github.com/$repo/blob/$branchName/$path"

            val result = buildString {
                appendLine(Strings.ai_search_github_file_title.str())
                appendLine(Strings.ai_search_label_repository.strOr(null, repo))
                appendLine(Strings.ai_search_label_path.strOr(null, path))
                appendLine(Strings.ai_search_label_branch.strOr(null, branchName))
                appendLine(Strings.ai_search_label_size.strOr(null, formatFileSize(content.length.toLong())))
                appendLine(
                    Strings.ai_search_label_lines.strOr(
                        null,
                        if (truncated) {
                            Strings.ai_search_lines_truncated.str(lines.size, maxLines)
                        } else {
                            Strings.ai_search_lines_total.str(lines.size)
                        }
                    )
                )
                appendLine(Strings.ai_search_label_url.strOr(null, htmlUrl))
                appendLine()
                appendLine(Strings.ai_search_label_content.str())
                appendLine("```")
                appendLine(displayContent)
                appendLine("```")

                if (truncated) {
                    appendLine()
                    appendLine(Strings.ai_search_content_truncated_note.str(lines.size))
                    appendLine(Strings.ai_search_content_truncated_hint.str())
                    appendLine(htmlUrl)
                }
            }

            val metadata = mapOf(
                "repo" to repo,
                "path" to path,
                "branch" to branchName,
                "size" to content.length,
                "totalLines" to lines.size,
                "displayedLines" to displayLines.size,
                "truncated" to truncated,
                "url" to htmlUrl,
                "method" to "raw"
            )

            ToolExecutionResult.Success(result, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
