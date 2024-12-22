package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.api.utils.RpcResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vultisig.keysign.v1.SuiCoin
import java.math.BigInteger
import javax.inject.Inject

interface SuiApi {

    suspend fun getBalance(
        address: String,
        contractAddress: String,
    ): BigInteger

    suspend fun getReferenceGasPrice(): BigInteger

    suspend fun getAllCoins(
        address: String
    ): List<SuiCoin>

    suspend fun executeTransactionBlock(
        unsignedTransaction: String,
        signature: String
    ): String

}

internal class SuiApiImpl @Inject constructor(
    private val http: HttpClient,
) : SuiApi {

    private val rpcUrl = "https://sui-rpc.publicnode.com"

    override suspend fun getBalance(address: String, contractAddress: String): BigInteger {
        val response = http.postRpc<RpcResponseJson>(
            url = rpcUrl,
            method = "suix_getBalance",
            params = buildJsonArray {
                add(address)
                if (contractAddress.isNotEmpty())
                    add(contractAddress)
            }
        )

        return response.result
            ?.jsonObject
            ?.get("totalBalance")
            ?.jsonPrimitive
            ?.content
            ?.let {
                BigInteger(it)
            } ?: error("Failed to get sui balance")
    }

    override suspend fun getReferenceGasPrice(): BigInteger {
        return http.postRpc<RpcResponseJson>(
            url = rpcUrl,
            method = "suix_getReferenceGasPrice",
            params = JsonArray(emptyList()),
        ).result
            ?.jsonPrimitive
            ?.content
            ?.let { BigInteger(it) }
            ?: error("Failed to fetch sui reference gas price")
    }

    override suspend fun getAllCoins(address: String): List<SuiCoin> {
        return http.postRpc<RpcResponseJson>(
            url = rpcUrl,
            method = "suix_getAllCoins",
            params = buildJsonArray {
                add(address)
            }
        ).result
            ?.jsonObject
            ?.get("data")
            ?.jsonArray
            ?.mapNotNull {
                val coinType = it.jsonObject["coinType"]?.jsonPrimitive?.content
                if (coinType != null) {
                    SuiCoin(
                        coinObjectId = it.jsonObject["coinObjectId"]?.jsonPrimitive?.content ?: "",
                        version = it.jsonObject["version"]?.jsonPrimitive?.content ?: "",
                        digest = it.jsonObject["digest"]?.jsonPrimitive?.content ?: "",
                        balance = it.jsonObject["balance"]?.jsonPrimitive?.content ?: "",
                        previousTransaction = it.jsonObject["previousTransaction"]?.jsonPrimitive?.content ?: "",
                        coinType = coinType,
                    )
                } else {
                    null
                }
            }
            ?: error("Failed to fetch all coins for sui")
    }

    override suspend fun executeTransactionBlock(
        unsignedTransaction: String,
        signature: String
    ): String {
        return http.postRpc<RpcResponseJson>(
            url = rpcUrl,
            method = "sui_executeTransactionBlock",
            params = buildJsonArray {
                add(unsignedTransaction)
                addJsonArray {
                    add(signature)
                }
            }
        ).result
            ?.jsonObject
            ?.get("digest")
            ?.jsonPrimitive
            ?.content
            ?: error("Failed to execute transaction block")
    }
}