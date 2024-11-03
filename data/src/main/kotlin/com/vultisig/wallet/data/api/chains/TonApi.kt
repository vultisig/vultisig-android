package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.utils.BigIntegerSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import javax.inject.Inject

interface TonApi {

    suspend fun getBalance(address: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String

    suspend fun getSpecificTransactionInfo(address: String): BigInteger
}

internal class TonApiImpl @Inject constructor(
    private val http: HttpClient,
) : TonApi {

    private val baseUrl: String = "https://api.vultisig.com/ton"

    override suspend fun getBalance(address: String): BigInteger =
        http.get("$baseUrl/v3/addressInformation") {
            parameter("address", address)
            parameter("use_v2", false)
        }.body<TonBalanceResponseJson>()
            .balance

    override suspend fun broadcastTransaction(transaction: String): String =
        http.post("$baseUrl/v2/sendBocReturnHash") {
            setBody(TonBroadcastTransactionRequestJson(transaction))
        }.body<TonBroadcastTransactionResponseJson>()
            .result
            .hash

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

}

@Serializable
private data class TonBalanceResponseJson(
    @SerialName("balance")
    @Serializable(with = BigIntegerSerializer::class)
    val balance: BigInteger,
)

@Serializable
private data class TonBroadcastTransactionRequestJson(
    @SerialName("boc")
    val boc: String,
)

@Serializable
private data class TonBroadcastTransactionResponseJson(
    @SerialName("result")
    val result: TonBroadcastTransactionResponseResultJson,
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



