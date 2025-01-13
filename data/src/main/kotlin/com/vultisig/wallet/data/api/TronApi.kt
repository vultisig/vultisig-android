package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TronBalanceResponseJson
import com.vultisig.wallet.data.api.models.TronBroadcastTxResponseJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockJson
import com.vultisig.wallet.data.api.models.TronTRC20BalanceResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

interface TronApi {

    suspend fun getBalance(
        coin: Coin,
    ): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getSpecific(): TronSpecificBlockJson

}

internal class TronApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : TronApi {

    private val rpcUrl = "https://tron-rpc.publicnode.com"

    override suspend fun broadcastTransaction(tx: String): String {
        val httpResponse = httpClient.post(rpcUrl) {
            url {
                path("wallet", "broadcasttransaction")
            }
            contentType(ContentType.Application.Json)
            setBody(tx)
        }
        val tronBroadcastTxResponseJson = httpResponse.body<TronBroadcastTxResponseJson>()
        return tronBroadcastTxResponseJson.txId.takeIf { tronBroadcastTxResponseJson.code == null }
            ?: throw Exception("Error broadcasting transaction: ${tronBroadcastTxResponseJson.code}")
    }

    override suspend fun getSpecific() =
        httpClient.post(rpcUrl) {
            url {
                path("wallet", "getnowblock")
            }
        }.body<TronSpecificBlockJson>()


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
        val paddedWalletAddress = "0".repeat(41) + walletAddress.drop(2)
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
            url = rpcUrl,
            method = "eth_call",
            params = params
        )
        return BigInteger(responseJson.result, 16)
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

}
