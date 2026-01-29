package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class EvmStatusProvider  @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : TransactionStatusProvider {

    private val rpcUrls = mapOf(
        Chain.Ethereum to "https://eth.llamarpc.com",
        Chain.Polygon to "https://polygon-rpc.com",
        Chain.Arbitrum to "https://arb1.arbitrum.io/rpc",
        Chain.Base to "https://mainnet.base.org",
        Chain.Optimism to "https://mainnet.optimism.io",
        Chain.Avalanche to "https://api.avax.network/ext/bc/C/rpc",
        Chain.BscChain to "https://bsc-dataseed.binance.org",
        Chain.Blast to "https://rpc.blast.io",
        Chain.CronosChain to "https://evm.cronos.org",
        Chain.ZkSync to "https://mainnet.era.zksync.io",
        Chain.Mantle to "https://rpc.mantle.xyz",
        Chain.Sei to "https://evm-rpc.sei-apis.com",
        Chain.Hyperliquid to "https://api.hyperliquid.xyz/evm"
    )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val rpcUrl = rpcUrls[chain] ?: return TransactionResult.Failed("Unknown chain")

            val response = httpClient.post(rpcUrl) {
                setBody(
                    """
                    {
                        "jsonrpc": "2.0",
                        "method": "eth_getTransactionReceipt",
                        "params": ["$txHash"],
                        "id": 1
                    }
                """.trimIndent()
                )
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject

            val result = json["result"]

            if (result == null || result is JsonNull) {
                TransactionResult.Pending
            } else {
                val receipt = result.jsonObject
                val status = receipt["status"]?.jsonPrimitive?.content

                when (status) {
                    "0x1" -> TransactionResult.Confirmed
                    "0x0" -> TransactionResult.Failed("Transaction reverted")
                    else -> TransactionResult.Pending
                }
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}