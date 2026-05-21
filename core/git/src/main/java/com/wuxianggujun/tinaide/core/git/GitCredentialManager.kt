package com.wuxianggujun.tinaide.core.git

interface GitCredentialManager {
    suspend fun saveHttpsCredential(host: String, username: String, token: String)
    suspend fun getHttpsCredential(host: String): GitCredential?
    suspend fun deleteHttpsCredential(host: String)

    suspend fun listHttpsHosts(): List<String>
}
