package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.utils.contentOrNull
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import wallet.core.jni.TONAddressConverter
import java.math.BigInteger
import java.util.Base64
import javax.inject.Inject

interface TonApi {

    suspend fun getBalance(address: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String?

    suspend fun getSpecificTransactionInfo(address: String): BigInteger

    suspend fun getWalletState(address: String): String

    suspend fun getJettonsBalance(address: String, contract: String): BigInteger

    suspend fun getJettonsAddress(address: String, contract: String): String
}

internal class TonApiImpl @Inject constructor(
    private val http: HttpClient,
) : TonApi {

    private val baseUrl: String = "https://api.vultisig.com/ton"

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    private suspend fun getAddressInformation(address: String): TonAddressInfoResponseJson =
        http.get("$baseUrl/v3/addressInformation") {
            parameter("address", address)
            parameter("use_v2", false)
        }.body<TonAddressInfoResponseJson>()

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
        getAddressInformation(address).status

    override suspend fun getJettonsBalance(address: String, contract: String): BigInteger {
        return runCatching {
            val tvmBoc = getJettonsAddress(address, contract)
            val jettonAddress = TONAddressConverter.fromBoc(tvmBoc)
            val jettonsUserAddress = TONAddressConverter.toUserFriendly(jettonAddress, true, false)

            runGetMethod(RunMethodRequestJson(jettonsUserAddress, GET_WALLET_DATA)).parseNum()

        }.getOrDefault(BigInteger.ZERO)
    }

    override suspend fun getJettonsAddress(address: String, contract: String): String {
        val bocAddress = TONAddressConverter.toBoc(address)

        val request = RunMethodRequestJson(
            address = contract,
            method = GET_WALLET_ADDRESS,
            stack = buildJsonArray {
                addJsonArray {
                    add(JsonPrimitive("tvm.Slice"))
                    add(JsonPrimitive(bocAddress))
                }
            }
        )

        return runGetMethod(request).parseCell()?.bytes ?: ""
    }

    private suspend fun runGetMethod(payload: RunMethodRequestJson): RunMethodResponseJson {
        return http.get("$baseUrl/runGetMethod") {
            setBody(payload)
        }.body<RunMethodResponseJson>()
    }

    private companion object {
        const val GET_WALLET_ADDRESS = "get_wallet_address"
        const val GET_WALLET_DATA = "get_wallet_data"
    }
}

@Serializable
private data class TonAddressInfoResponseJson(
    @SerialName("balance")
    @Contextual
    val balance: BigInteger,
    @SerialName("status")
    val status: String,
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

@Serializable
private data class RunMethodRequestJson(
    val address: String,
    val method: String,
    val stack: JsonElement = buildJsonArray { },
)

@Serializable
data class Cell(
    val bytes: String,
)

@Serializable
data class RunMethodResponseJson(
    @SerialName("exit_code")
    val code: Int = 0,
    val stack: List<List<JsonElement>> = emptyList(),
)  {
    fun parseCell(): Cell? {
        val first = stack.firstOrNull()?.getOrNull(0)?.contentOrNull
        if (first != "cell") return null

        val bytes = stack.getOrNull(0)
            ?.getOrNull(1)
            ?.jsonObject
            ?.get("bytes")
            ?.contentOrNull

        return bytes?.let { Cell(it) }
    }

    fun parseNum(): BigInteger {
        val type = stack.firstOrNull()?.getOrNull(0)?.contentOrNull
        if (type != "num") return BigInteger.ZERO

        val value = stack.getOrNull(0)
            ?.getOrNull(1)
            ?.contentOrNull

        return value?.convertToBigIntegerOrZero() ?: BigInteger.ZERO
    }
}
