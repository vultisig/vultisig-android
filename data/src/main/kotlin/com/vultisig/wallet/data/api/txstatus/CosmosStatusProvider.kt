package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject

class CosmosStatusProvider @Inject constructor(private val httpClient: HttpClient) : TransactionStatusProvider {

    private val apiUrls = mapOf(
        Chain.GaiaChain to "https://rest.cosmos.directory/cosmoshub/cosmos/tx/v1beta1/txs",
        Chain.Kujira to "https://rest.cosmos.directory/kujira/cosmos/tx/v1beta1/txs",
        Chain.Osmosis to "https://rest.cosmos.directory/osmosis/cosmos/tx/v1beta1/txs",
        Chain.Dydx to "https://rest.cosmos.directory/dydx/cosmos/tx/v1beta1/txs",
        Chain.Terra to "https://rest.cosmos.directory/terra/cosmos/tx/v1beta1/txs",
        Chain.TerraClassic to "https://rest.cosmos.directory/terra2/cosmos/tx/v1beta1/txs",
        Chain.Noble to "https://rest.cosmos.directory/noble/cosmos/tx/v1beta1/txs",
        Chain.Akash to "https://rest.cosmos.directory/akash/cosmos/tx/v1beta1/txs"
    )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val baseUrl = apiUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
            val response = httpClient.get("$baseUrl/$txHash")

            // HTTP 200 means transaction found and confirmed
            // HTTP 404 means transaction not found (pending)
            if (response.status.value == 200) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}