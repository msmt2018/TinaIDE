package com.wuxianggujun.tinaide.ai.api

import okhttp3.Request

/**
 * 请求鉴权策略。
 *
 * 目的:把"Gateway(JWT 走 OkHttp 拦截器) vs BYOK(Bearer 自行附加)"两种鉴权路径
 * 正交出来,让 [AiApiClient] 不再关心 `AiConfig.accessMode`,也不再出现
 * `config.copy(apiKey = "")` 这种为了绕一个分支写的 hack。
 */
sealed interface AuthStrategy {

    /** 在请求构造完成前调用;实现可以增加 Header、签名等。 */
    fun apply(builder: Request.Builder)

    /** 由 `TinaServerHttpClientFactory.authenticated(...)` 的拦截器附加 JWT,这里无需额外处理。 */
    data object Gateway : AuthStrategy {
        override fun apply(builder: Request.Builder) {
            // 无操作:鉴权由拦截器注入。
        }
    }

    /**
     * 用户自带 API Key 的 BYOK 模式。
     *
     * 注意:[apiKey] 在构造时就应该是干净的(已经 trim + 去换行),
     * 请求路径不再重复做清洗——DRY 原则要求"清洗在保存时发生"。
     */
    data class Bearer(val apiKey: String) : AuthStrategy {
        override fun apply(builder: Request.Builder) {
            if (apiKey.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
    }
}
