package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.vultisig.wallet.data.api.models.BlowfishRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

internal interface BlowfishApi {
    suspend fun fetchBlowfishTransactions(chain: String, network: String, blowfishRequest: BlowfishRequest) : String

    suspend fun fetchBlowfishSolanaTransactions() : String
}

internal class BlowfishApiImpl @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : BlowfishApi {
    override suspend fun fetchBlowfishTransactions(chain: String, network: String, blowfishRequest: BlowfishRequest) : String {
        val response = httpClient
            .post("https://api.vultisig.com/blowfish/$chain/v0/$network/scan/transactions?language=en&method=eth_sendTransaction") {
                contentType(ContentType.Application.Json)
                header("X-Api-Version", "2023-06-05")
                setBody(gson.toJson(blowfishRequest))
            }
        return ""
    }

    override suspend fun fetchBlowfishSolanaTransactions() : String {
        val response = httpClient
            .get("https://api.vultisig.com//blowfish/solana/v0/mainnet/scan/transactions?language=en")
        return ""
    }
}