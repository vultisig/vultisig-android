package com.vultisig.wallet.data.api.chains.ton

import java.math.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class TonAddressInfoResponseJson(
    @SerialName("balance") @Contextual val balance: BigInteger,
    @SerialName("status") val status: String,
)

@Serializable
internal data class TonBroadcastTransactionRequestJson(@SerialName("boc") val boc: String)

@Serializable
internal data class TonBroadcastTransactionResponseJson(
    @SerialName("result") val result: TonBroadcastTransactionResponseResultJson?,
    @SerialName("error") val error: String?,
)

@Serializable
internal data class TonBroadcastTransactionResponseResultJson(@SerialName("hash") val hash: String)

@Serializable
internal data class TonSpecificTransactionInfoResponseJson(
    @SerialName("result") val result: TonSpecificTransactionInfoResponseResultJson
)

@Serializable
internal data class TonSpecificTransactionInfoResponseResultJson(
    @SerialName("account_state")
    val accountState: TonSpecificTransactionInfoResponseAccountStateJson
)

@Serializable
internal data class TonSpecificTransactionInfoResponseAccountStateJson(
    @SerialName("seqno") val seqno: JsonPrimitive?
)

@Serializable
data class JettonWalletsJson(
    @SerialName("jetton_wallets") val jettonWallets: List<JettonWalletJson> = emptyList(),
    @SerialName("address_book") val addressBook: Map<String, AddressEntryJson> = emptyMap(),
) {
    fun getJettonsAddress(): String? {
        val jettonAddress = jettonWallets.firstOrNull()?.address ?: ""
        val address = addressBook[jettonAddress]
        return address?.userFriendly
    }
}

@Serializable
data class JettonWalletJson(
    @SerialName("address") val address: String,
    @SerialName("jetton") val jetton: String,
    @SerialName("balance") val balance: String,
)

@Serializable data class AddressEntryJson(@SerialName("user_friendly") val userFriendly: String)

@Serializable
data class TonEstimateFeeJson(
    @SerialName("ok") val ok: Boolean,
    @SerialName("result") val result: TonFeeResult? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("code") val code: Int? = null,
)

@Serializable
data class TonFeeResult(
    @SerialName("@type") val type: String,
    @SerialName("source_fees") val sourceFees: TonFees,
    @SerialName("destination_fees") val destinationFees: List<TonFees> = emptyList(),
    @SerialName("@extra") val extra: String? = null,
)

@Serializable
data class TonFees(
    @SerialName("@type") val type: String,
    @SerialName("in_fwd_fee") val inFwdFee: Long,
    @SerialName("storage_fee") val storageFee: Long,
    @SerialName("gas_fee") val gasFee: Long,
    @SerialName("fwd_fee") val fwdFee: Long,
) {
    fun totalFee(): Long = inFwdFee + storageFee + gasFee + fwdFee
}

@Serializable
data class TonStatusResult(
    @SerialName("transactions") val transactions: List<TransactionJson> = emptyList()
)

@Serializable
data class TransactionJson(
    @SerialName("description") val description: TonTransactionDescriptionJson? = null
)

@Serializable
data class TonTransactionDescriptionJson(
    @SerialName("aborted") val aborted: Boolean? = null,
    @SerialName("destroyed") val destroyed: Boolean? = null,
)
