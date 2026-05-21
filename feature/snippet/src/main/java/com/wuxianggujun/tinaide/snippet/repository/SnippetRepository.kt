package com.wuxianggujun.tinaide.snippet.repository

import android.content.Context
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.core.network.server.TinaServerConfig
import com.wuxianggujun.tinaide.snippet.api.CopyResponse
import com.wuxianggujun.tinaide.snippet.api.CreateSnippetRequest
import com.wuxianggujun.tinaide.snippet.api.FavoriteResponse
import com.wuxianggujun.tinaide.snippet.api.RateResponse
import com.wuxianggujun.tinaide.snippet.api.SnippetMarketplaceApi
import com.wuxianggujun.tinaide.snippet.model.SnippetDetail
import com.wuxianggujun.tinaide.snippet.model.SnippetListData

/**
 * 代码片段仓库
 *
 * 通过 Koin 以 single 方式注入，不再使用手动单例。
 */
class SnippetRepository(
    private val context: Context
) {
    private suspend fun api(): SnippetMarketplaceApi {
        val baseUrl = TinaServerConfig.getInstance(context).getServerUrl()
        return SnippetMarketplaceApi.create(context, baseUrl)
    }

    suspend fun listSnippets(
        page: Int = 1,
        limit: Int = 20,
        search: String? = null,
        authorId: String? = null,
        includeDrafts: Boolean? = null
    ): ApiResult<SnippetListData> {
        return api().listSnippets(
            page = page,
            limit = limit,
            search = search,
            authorId = authorId,
            includeDrafts = includeDrafts
        )
    }

    suspend fun getSnippetDetail(id: String): ApiResult<SnippetDetail> {
        return api().getSnippetDetail(id)
    }

    suspend fun recordCopy(id: String): ApiResult<CopyResponse> {
        return api().recordCopy(id)
    }

    suspend fun favorite(id: String): ApiResult<FavoriteResponse> {
        return api().favorite(id)
    }

    suspend fun unfavorite(id: String): ApiResult<FavoriteResponse> {
        return api().unfavorite(id)
    }

    suspend fun rate(id: String, rating: Int): ApiResult<RateResponse> {
        return api().rate(id, rating)
    }

    suspend fun createSnippet(
        title: String,
        description: String?,
        codeContent: String,
        language: String,
        isDraft: Boolean = false
    ): ApiResult<SnippetDetail> {
        return api().createSnippet(
            CreateSnippetRequest(
                title = title,
                description = description,
                codeContent = codeContent,
                language = language,
                isDraft = isDraft
            )
        )
    }
}
