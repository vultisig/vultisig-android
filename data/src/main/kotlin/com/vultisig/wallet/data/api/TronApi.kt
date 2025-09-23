package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountRequestJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronBalanceResponseJson
import com.vultisig.wallet.data.api.models.TronBroadcastTxResponseJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.api.models.TronContractInfoJson
import com.vultisig.wallet.data.api.models.TronContractRequestJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockJson
import com.vultisig.wallet.data.api.models.TronTriggerConstantContractJson
import com.vultisig.wallet.data.chains.helpers.TronFunctions.buildTrc20TransferParameters
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

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
        functionSelector: String,
        amount: BigInteger
    ): Long

    suspend fun getChainParameters(): TronChainParametersJson

    suspend fun getAccountResource(address: String): TronAccountResourceJson

    suspend fun getAccount(address: String): TronAccountJson

    suspend fun getContractMetadata(contract: String): TronContractInfoJson
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
        functionSelector: String,
        amount: BigInteger
    ): Long {
        val parameter =
            buildTrc20TransferParameters(
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

    override suspend fun getChainParameters(): TronChainParametersJson {
        return httpClient.post(rpcUrl) {
            url {
                path("wallet", "getchainparameters")
            }
        }.body<TronChainParametersJson>()
    }

    override suspend fun getBalance(coin: Coin): BigInteger {
        try {
            val response = httpClient.get("$tronGrid/v1/accounts/${coin.address}")
            val content = response.body<TronBalanceResponseJson>()
            return if (coin.isNativeToken)
                content.tronBalanceResponseData[0].balance
            else
                content.tronBalanceResponseData[0].trc20[0][coin.contractAddress]
                    ?: BigInteger.ZERO
        } catch (e: Exception) {
            Timber.e(e, "error getting tron balance")
            return BigInteger.ZERO
        }
    }

    override suspend fun getAccountResource(address: String): TronAccountResourceJson {
        return httpClient.post(rpcUrl) {
            url {
                url { path("wallet", "getaccountresource") }
            }
            contentType(ContentType.Application.Json)
            setBody(TronAccountRequestJson(address, true))
        }.bodyOrThrow<TronAccountResourceJson>()
    }

    override suspend fun getAccount(address: String): TronAccountJson {
        return httpClient.post(rpcUrl) {
            url {
                appendPathSegments("/wallet/getaccount")
            }
            contentType(ContentType.Application.Json)
            setBody(TronAccountRequestJson(address, true))
        }.bodyOrThrow<TronAccountJson>()
    }

    override suspend fun getContractMetadata(contract: String): TronContractInfoJson {
        return httpClient.post(rpcUrl) {
            url {
                appendPathSegments("/wallet/getcontractinfo")
            }
            contentType(ContentType.Application.Json)
            setBody(TronContractRequestJson(contract))
        }.bodyOrThrow<TronContractInfoJson>()
    }

    companion object {
        const val TRANSFER_FUNCTION_SELECTOR = "transfer(address,uint256)"
        private const val ENERGY_TO_SUN_FACTOR = 280
        private const val DUP_TRANSACTION_ERROR_CODE = "DUP_TRANSACTION_ERROR"
    }
}
