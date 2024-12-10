package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import javax.inject.Inject

interface EtherfaceApi {
    suspend fun decodeFunction(hash: String): String
}

class EtherfaceApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : EtherfaceApi {
    override suspend fun decodeFunction(hash: String): String {
        val response = httpClient.post("https://api.etherface.io/v1/signatures/hash/all/$hash/1")
        return response.toString()
    }
}