package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TonEstimateFeeResponse(
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
    /**
     * Calculate the total fee from all components
     * For TON, the main fee is usually in_fwd_fee
     */
    fun totalFee(): Long = inFwdFee + storageFee + gasFee + fwdFee
}