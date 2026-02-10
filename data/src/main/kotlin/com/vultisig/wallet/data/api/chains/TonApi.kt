package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.utils.bodyOrThrow
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

    suspend fun getJettonBalance(address: String, contract: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String?

    suspend fun getSpecificTransactionInfo(address: String): BigInteger

    suspend fun getWalletState(address: String): String

    suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson

    suspend fun getEstimateFee(address: String, serializedBoc: String): BigInteger
    suspend fun getTsStatus(txHash: String): TonStatusResult
}

internal class TonApiImpl @Inject constructor(
    private val http: HttpClient,
) : TonApi {

    private val baseUrl: String = "https://api.vultisig.com/ton"

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    override suspend fun getJettonBalance(address: String, contract: String): BigInteger {
        val wallet = getJettonWallet(
            address,
            contract
        ).jettonWallets.firstOrNull()
        return wallet?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO
    }

    private suspend fun getAddressInformation(address: String): TonAddressInfoResponseJson =
        http.get("$baseUrl/v3/addressInformation") {
            parameter(
                "address",
                address
            )
            parameter(
                "use_v2",
                false
            )
        }.body<TonAddressInfoResponseJson>()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun broadcastTransaction(transaction: String): String? {
        val body = http.post("$baseUrl/v2/sendBocReturnHash") {
            setBody(TonBroadcastTransactionRequestJson(transaction))
        }
        val response = body.body<TonBroadcastTransactionResponseJson>()
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
            parameter(
                "address",
                address
            )
        }.body<TonSpecificTransactionInfoResponseJson>()
            .result
            .accountState
            .seqno
            ?.content
            ?.let { BigInteger(it) }
            ?: BigInteger.ZERO

    override suspend fun getWalletState(address: String): String =
        getAddressInformation(address).status

    override suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson {
        return http.get("$baseUrl/v3/jetton/wallets") {
            parameter(
                "owner_address",
                address
            )
            parameter(
                "jetton_master_address",
                contract
            )
        }.bodyOrThrow<JettonWalletsJson>()
    }

    override suspend fun getEstimateFee(address: String, serializedBoc: String): BigInteger {
        val feeResponse = http.get("$baseUrl/v3/estimateFee") {
            parameter(
                "address",
                address
            )
            parameter(
                "body",
                serializedBoc
            )
            parameter(
                "ignore_chksig",
                true
            )
        }.bodyOrThrow<TonEstimateFeeJson>()

        return feeResponse.result?.sourceFees?.totalFee()?.toBigInteger()
            ?: throw Exception("Can't calculate Fees")
    }

    override suspend fun getTsStatus(
        txHash: String
    ): TonStatusResult {
        val response = http.get("${baseUrl}/v3/transactionsByMessage?msg_hash=${txHash}")
        return response.bodyOrThrow<TonStatusResult>()
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
data class JettonWalletsJson(
    @SerialName("jetton_wallets")
    val jettonWallets: List<JettonWalletJson> = emptyList(),
    @SerialName("address_book")
    val addressBook: Map<String, AddressEntryJson> = emptyMap()
) {
    fun getJettonsAddress(): String? {
        val jettonAddress = jettonWallets.firstOrNull()?.address ?: ""
        val address = addressBook[jettonAddress]
        return address?.userFriendly
    }
}

@Serializable
data class JettonWalletJson(
    @SerialName("address")
    val address: String,
    @SerialName("jetton")
    val jetton: String,
    @SerialName("balance")
    val balance: String,
)

@Serializable
data class AddressEntryJson(
    @SerialName("user_friendly")
    val userFriendly: String,
)

@Serializable
data class TonEstimateFeeJson(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: TonFeeResult? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("code")
    val code: Int? = null
)

@Serializable
data class TonFeeResult(
    @SerialName("@type")
    val type: String,
    @SerialName("source_fees")
    val sourceFees: TonFees,
    @SerialName("destination_fees")
    val destinationFees: List<TonFees> = emptyList(),
    @SerialName("@extra")
    val extra: String? = null
)

@Serializable
data class TonFees(
    @SerialName("@type")
    val type: String,
    @SerialName("in_fwd_fee")
    val inFwdFee: Long,
    @SerialName("storage_fee")
    val storageFee: Long,
    @SerialName("gas_fee")
    val gasFee: Long,
    @SerialName("fwd_fee")
    val fwdFee: Long
) {
    fun totalFee(): Long = inFwdFee + storageFee + gasFee + fwdFee
}

@Serializable
data class TonStatusResult(
    @SerialName("transactions")
    val transactions: List<TransactionJson> = emptyList(),
)

@Serializable
data class TransactionJson(
    @SerialName("finality")
    val finality: String? = null
)

