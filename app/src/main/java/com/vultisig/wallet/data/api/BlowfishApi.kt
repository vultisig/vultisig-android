package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.vultisig.wallet.data.api.models.BlowfishRequest
import com.vultisig.wallet.data.api.models.BlowfishResponse
import com.vultisig.wallet.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

internal interface BlowfishApi {
    suspend fun fetchBlowfishTransactions(chain: String, network: String, blowfishRequest: BlowfishRequest) : BlowfishResponse

    suspend fun fetchBlowfishSolanaTransactions(blowfishRequest: BlowfishRequest) : BlowfishResponse
}

internal class BlowfishApiImpl @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : BlowfishApi {
    override suspend fun fetchBlowfishTransactions(
        chain: String,
        network: String,
        blowfishRequest: BlowfishRequest,
    ) : BlowfishResponse {
        val response = httpClient
            .post("https://api.vultisig.com/blowfish/$chain/v0/$network/scan/transactions?language=en&method=eth_sendTransaction") {
                contentType(ContentType.Application.Json)
                header("X-Api-Version", "2023-06-05")
                setBody(gson.toJson(blowfishRequest))
            }

        return gson.fromJson(response.bodyAsText(), BlowfishResponse::class.java)
    }

    override suspend fun fetchBlowfishSolanaTransactions(
        blowfishRequest: BlowfishRequest,
    ) : BlowfishResponse {
        val response = httpClient
            .post("https://api.vultisig.com/blowfish/solana/v0/mainnet/scan/transactions?language=en"){
                contentType(ContentType.Application.Json)
                header("X-Api-Version", "2023-06-05")
                setBody(gson.toJson(blowfishRequest))
            }

        return gson.fromJson(response.bodyAsText(), BlowfishResponse::class.java)
    }
}

internal val Chain.blowfishChainName: String?
    get() = when (this) {
        Chain.ethereum -> "ethereum"
        Chain.polygon -> "polygon"
        Chain.avalanche -> "avalanche"
        Chain.arbitrum -> "arbitrum"
        Chain.optimism -> "optimism"
        Chain.base -> "base"
        Chain.blast -> "blast"
        Chain.bscChain -> "bnb"
        Chain.solana -> "solana"
        else -> null
    }


internal val Chain.blowfishNetwork: String?
    get() = when (this) {
        Chain.ethereum, Chain.polygon, Chain.avalanche, Chain.optimism, Chain.base, Chain.blast,
        Chain.bscChain, Chain.solana -> "mainnet"

        Chain.arbitrum -> "one"
        else -> null
    }