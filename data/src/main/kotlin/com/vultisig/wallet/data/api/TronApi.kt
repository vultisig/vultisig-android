package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TronBalanceResponseJson
import com.vultisig.wallet.data.api.models.TronBroadcastTxResponseJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockJson
import com.vultisig.wallet.data.api.models.TronTRC20BalanceResponseJson
import com.vultisig.wallet.data.api.models.TronTriggerConstantContractJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import wallet.core.jni.Base58
import java.math.BigInteger
import javax.inject.Inject
import kotlin.math.max

interface TronApi {

    suspend fun getBalance(
        coin: Coin,
    ): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getSpecific(): TronSpecificBlockJson

    suspend fun getTriggerConstantContractFee(
        ownerAddressBase58: String,
        contractAddressBase58: String,
        recipientAddressHex: String,
        amount: BigInteger
    ): Long

}

internal class TronApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : TronApi {

    private val rpcUrl = "https://tron-rpc.publicnode.com"
    private val tronGrid = "https://api.trongrid.io"

    override suspend fun broadcastTransaction(tx: String): String {
        val httpResponse = httpClient.post(rpcUrl) {
            url {
                path("wallet", "broadcasttransaction")
            }
            contentType(ContentType.Application.Json)
            setBody(tx)
        }
        val tronBroadcastTxResponseJson = httpResponse.body<TronBroadcastTxResponseJson>()
        return tronBroadcastTxResponseJson.txId.takeIf {
            tronBroadcastTxResponseJson.code in listOf(null, DUP_TRANSACTION_ERROR_CODE)
        } ?: throw Exception("Error broadcasting transaction: ${tronBroadcastTxResponseJson.code}")
    }

    override suspend fun getSpecific() =
        httpClient.post(rpcUrl) {
            url {
                path("wallet", "getnowblock")
            }
        }.body<TronSpecificBlockJson>()

    override suspend fun getTriggerConstantContractFee(
        ownerAddressBase58: String,
        contractAddressBase58: String,
        recipientAddressHex: String,
        amount: BigInteger
    ): Long {
        val functionSelector = FUNCTION_SELECTOR
        val parameter =
            buildTrc20TransParameter(
                recipientBaseHex = recipientAddressHex,
                amount = amount
            )
        val body = buildJsonObject {
            put("owner_address", ownerAddressBase58)
            put("contract_address", contractAddressBase58)
            put("function_selector", functionSelector)
            put("parameter", parameter)
            put("visible", true)
        }
        val triggerConstant = httpClient.post(tronGrid) {
            url {
                path("walletsolidity", "triggerconstantcontract")
            }
            setBody(body)
            accept(ContentType.Application.Json)
        }.body<TronTriggerConstantContractJson>()
        val totalEnergy = triggerConstant.energyUsed + triggerConstant.energyPenalty
        val totalSun = totalEnergy * ENERGY_TO_SUN_FACTOR
        return totalSun
    }

    private fun buildTrc20TransParameter(recipientBaseHex: String, amount: BigInteger): String {
        val paddedAddressHex = "0".repeat(24) + recipientBaseHex.stripHexPrefix()
        val amountHex = amount.toString(16)
        val paddedAmountHex = "0".repeat(max(0, 64 - amountHex.count())) + amountHex
        return paddedAddressHex + paddedAmountHex
    }


    override suspend fun getBalance(coin: Coin): BigInteger {
        try {
            return if (coin.isNativeToken)
                getTronBalance(coin.address)
            else getTRC20Balance(coin.address, coin.contractAddress)
        } catch (e: Exception) {
            Timber.e(e, "error getting tron balance")
            return BigInteger.ZERO
        }
    }


    private suspend fun getTRC20Balance(address: String, contractAddress: String): BigInteger {
        val walletAddress = Numeric.toHexString(Base58.decode(address))
        val hexContractAddress = Numeric.toHexString(Base58.decode(contractAddress))
        val paddedWalletAddress = "0".repeat(22) + walletAddress.drop(2)
        val data = "0x70a08231$paddedWalletAddress"
        val fromAddress = "0x${walletAddress.drop(4)}"
        val toAddress = "0x${hexContractAddress.drop(4)}"
        val params = buildJsonArray {
            add(
                buildJsonObject {
                    put("from", fromAddress)
                    put("to", toAddress)
                    put("gas", "0x0")
                    put("gasPrice", "0x0")
                    put("value", "0x0")
                    put("data", data)
                }
            )
            add("latest")
        }

        val responseJson = httpClient.postRpc<TronTRC20BalanceResponseJson>(
            url = "$tronGrid/jsonrpc",
            method = "eth_call",
            params = params
        )
        return BigInteger(responseJson.result.stripHexPrefix(), 16)
    }

    private suspend fun getTronBalance(address: String) = httpClient.post(rpcUrl) {
        url {
            path("wallet", "getaccount")
        }
        setBody(
            buildJsonObject {
                put("address", address)
                put("visible", true)
            }
        )
    }.body<TronBalanceResponseJson>().balance


    companion object {
        private const val FUNCTION_SELECTOR = "transfer(address,uint256)"
        private const val ENERGY_TO_SUN_FACTOR = 280
        private const val DUP_TRANSACTION_ERROR_CODE = "DUP_TRANSACTION_ERROR"
    }
}
