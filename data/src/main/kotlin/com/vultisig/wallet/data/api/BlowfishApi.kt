package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.BlowfishRequest
import com.vultisig.wallet.data.api.models.BlowfishResponse
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val HEADER_API_TITLE = "X-Api-Version"
private const val BLOWFISH_API_VERSION = "2023-06-05"
interface BlowfishApi {
    suspend fun fetchBlowfishTransactions(chain: String, network: String, blowfishRequest: BlowfishRequest) : BlowfishResponse

    suspend fun fetchBlowfishSolanaTransactions(blowfishRequest: BlowfishRequest) : BlowfishResponse
}

internal class BlowfishApiImpl @Inject constructor(
    private val json: Json,
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
                header(HEADER_API_TITLE, BLOWFISH_API_VERSION)
                setBody(blowfishRequest)
            }

        return response.body<BlowfishResponse>()
    }

    override suspend fun fetchBlowfishSolanaTransactions(
        blowfishRequest: BlowfishRequest,
    ) : BlowfishResponse {
        val response = httpClient
            .post("https://api.vultisig.com/blowfish/solana/v0/mainnet/scan/transactions?language=en"){
                contentType(ContentType.Application.Json)
                header(HEADER_API_TITLE, BLOWFISH_API_VERSION)
                setBody(blowfishRequest)
            }

        return response.body<BlowfishResponse>()
    }
}

val Chain.blowfishChainName: String?
    get() = when (this) {
        Chain.Ethereum -> "ethereum"
        Chain.Polygon -> "polygon"
        Chain.Avalanche -> "avalanche"
        Chain.Arbitrum -> "arbitrum"
        Chain.Optimism -> "optimism"
        Chain.Base -> "base"
        Chain.Blast -> "blast"
        Chain.BscChain -> "bnb"
        Chain.Solana -> "solana"
        else -> null
    }


val Chain.blowfishNetwork: String?
    get() = when (this) {
        Chain.Ethereum, Chain.Polygon, Chain.Avalanche, Chain.Optimism, Chain.Base, Chain.Blast,
        Chain.BscChain, Chain.Solana -> "mainnet"

        Chain.Arbitrum -> "one"
        else -> null
    }