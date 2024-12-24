package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.LiQuestResponseJson
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

internal interface LiQuestApi {
    suspend fun getLifiContractPriceUsd(
        chain: Chain,
        contractAddresses: String,
    ): LiQuestResponseJson
}

internal class LiQuestApiImpl @Inject constructor(
    private val http: HttpClient,
) : LiQuestApi {

    override suspend fun getLifiContractPriceUsd(
        chain: Chain,
        contractAddresses: String,
    ): LiQuestResponseJson = http.get("https://li.quest/v1/token") {
        parameter("chain", chain.lifiChainId)
        parameter("token", contractAddresses)
    }.body()


    private val Chain.lifiChainId: String
        get() = when (this) {
            Chain.Ethereum -> "eth"
            else -> error("lifi chain id not found for chain $this")
        }
}

