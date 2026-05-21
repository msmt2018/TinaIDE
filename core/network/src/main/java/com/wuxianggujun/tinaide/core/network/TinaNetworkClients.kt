package com.wuxianggujun.tinaide.core.network

import okhttp3.OkHttpClient

object TinaNetworkClients {
    private val dohResolver by lazy { DohDnsResolver() }
    private val smartDnsResolver by lazy { SmartDnsResolver(dohDnsResolver = dohResolver) }
    private val smartDns by lazy { OkHttpSmartDns(smartDnsResolver) }

    val probeClient: OkHttpClient by lazy {
        OkHttpClientProvider.probe
    }

    fun diagnoseHost(host: String): SmartDnsResolver.Resolution = smartDnsResolver.resolve(host)
}
