package com.vultisig.wallet.data.api.chains

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import java.util.Base64
import javax.inject.Inject

interface TonApi {

    suspend fun getBalance(address: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String?

    suspend fun getSpecificTransactionInfo(address: String): BigInteger

    suspend fun getWalletState(address: String): String
}

internal class TonApiImpl @Inject constructor(
    private val http: HttpClient,
) : TonApi {

    private val baseUrl: String = "https://api.vultisig.com/ton"

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    private suspend fun getAddressInformation(address: String): TonAddressStateResponseJson =
        http.get("$baseUrl/v3/addressInformation") {
            parameter("address", address)
            parameter("use_v2", false)
        }.body<TonAddressStateResponseJson>()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun broadcastTransaction(transaction: String): String? {
        val response = http.post("$baseUrl/v2/sendBocReturnHash") {
            setBody(TonBroadcastTransactionRequestJson(transaction))
        }.body<TonBroadcastTransactionResponseJson>()
        if (response.error != null) {
            if (response.error.contains("duplicate message")) {
                return null
            }
            throw Exception("Error broadcasting transaction: ${response.error}")
        }
        if (response.result == null) {
            return null
        }
        // The API returns a Base64-encoded hash that needs to be converted to hex format
        val decodedBytes = Base64.getDecoder().decode(response.result.hash)
        return decodedBytes.toHexString()
    }

    override suspend fun getSpecificTransactionInfo(
        address: String,
    ): BigInteger =
        http.get("$baseUrl/v2/getExtendedAddressInformation") {
            parameter("address", address)
        }.body<TonSpecificTransactionInfoResponseJson>()
            .result
            .accountState
            .seqno
            ?.content
            ?.let { BigInteger(it) }
            ?: BigInteger.ZERO

    override suspend fun getWalletState(address: String): String =
        getAddressInformation(address).state
}

@Serializable
private data class TonAddressStateResponseJson(
    @SerialName("balance")
    @Contextual
    val balance: BigInteger,
    @SerialName("state")
    val state: String,
)

@Serializable
private data class TonBroadcastTransactionRequestJson(
    @SerialName("boc")
    val boc: String,
)

@Serializable
private data class TonBroadcastTransactionResponseJson(
    @SerialName("result")
    val result: TonBroadcastTransactionResponseResultJson?,
    @SerialName("error")
    val error: String?,
)

@Serializable
private data class TonBroadcastTransactionResponseResultJson(
    @SerialName("hash")
    val hash: String,
)

@Serializable
private data class TonSpecificTransactionInfoResponseJson(
    @SerialName("result")
    val result: TonSpecificTransactionInfoResponseResultJson,
)

@Serializable
private data class TonSpecificTransactionInfoResponseResultJson(
    @SerialName("account_state")
    val accountState: TonSpecificTransactionInfoResponseAccountStateJson,
)

@Serializable
private data class TonSpecificTransactionInfoResponseAccountStateJson(
    @SerialName("seqno")
    val seqno: JsonPrimitive?,
)



