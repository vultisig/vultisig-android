package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject


internal interface PolkadotApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getBlockHash(isGenesis: Boolean = false): String
    suspend fun getGenesisBlockHash(): String
    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>
    suspend fun getBlockHeader(): BigInteger
    suspend fun broadcastTransaction(tx: String): String?
}

internal class PolkadotApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : PolkadotApi {
    private val polkadotApiUrl = "https://polkadot-rpc.publicnode.com"
    private val polkadotBalanceApiUrl = "https://polkadot.api.subscan.io/api/v2/scan/search"


    override suspend fun getBalance(address: String): BigInteger {
        val bodyMap = mapOf(
            "key" to address
        )
        val response = httpClient
            .post(polkadotBalanceApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(bodyMap))
            }
        val rpcResp = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        val respCode = rpcResp.get("code").asInt
        if (respCode == 10004) {
            return BigInteger.ZERO
        }
        val balance =
            rpcResp.get("data").asJsonObject.get("account").asJsonObject.get("balance").asBigDecimal
        return balance.multiply(BigDecimal(10000000000)).toBigInteger()
    }

    override suspend fun getNonce(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "system_accountNextIndex",
            params = listOf(address),
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)
        return rpcResp.get("result").asBigInteger
    }

    override suspend fun getBlockHash(isGenesis: Boolean): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getBlockHash",
            params = if (isGenesis) listOf(0) else listOf(),
            id = 1,
        )

        val response = httpClient.post(polkadotApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)
        return rpcResp.get("result").asString
    }

    override suspend fun getGenesisBlockHash(): String {
        return getBlockHash(true)
    }

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "state_getRuntimeVersion",
            params = listOf(),
            id = 1,
        )

        val response = httpClient.post(polkadotApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)
        val specVersion = rpcResp.get("result").asJsonObject.get("specVersion").asBigInteger
        val transactionVersion =
            rpcResp.get("result").asJsonObject.get("transactionVersion").asBigInteger
        return Pair(specVersion, transactionVersion)
    }

    override suspend fun getBlockHeader(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getHeader",
            params = listOf(),
            id = 1,
        )

        val response = httpClient.post(polkadotApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)
        val number = rpcResp.get("result").asJsonObject.get("number").asString
        return BigInteger(number.drop(2), 16)
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "author_submitExtrinsic",
            params = listOf(if (tx.startsWith("0x")) tx else "0x${tx}"),
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            if (rpcResp.error.code == 1012) {
                return null
            }
            throw Exception("Error broadcasting transaction: $responseContent")
        }
        return rpcResp.result
    }
}