package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.ThorBalancesResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

internal interface ThorBalanceApi {

    suspend fun getEthBalances(
        chainId: Int?,
        address: String,
    ): ThorBalancesResponseJson

    suspend fun getBalances(
        chainName: String,
        address: String,
    ): ThorBalancesResponseJson

}

internal class ThorBalanceApiImpl @Inject constructor(
    private val http: HttpClient,
) : ThorBalanceApi {

    private val baseUrl = "https://api-v2-prod.thorwallet.org"

    override suspend fun getEthBalances(
        chainId: Int?,
        address: String
    ): ThorBalancesResponseJson =
        http.get("$baseUrl/balance/eth/$address") {
            if (chainId != null) {
                parameter("chain", chainId)
            }
        }.body()

    override suspend fun getBalances(
        chainName: String,
        address: String
    ): ThorBalancesResponseJson =
        http.get("$baseUrl/balance/$chainName/$address")
            .body()

}