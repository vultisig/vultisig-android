package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface CosmosTHORChainAccountResponse {
    data class Success(val response: THORChainAccountJson) : CosmosTHORChainAccountResponse
    data class Error(val response: THORChainAccountErrorJson) : CosmosTHORChainAccountResponse
}

@Serializable
data class THORChainAccountJson(
    @SerialName("account")
    val account: THORChainAccountValue?,
)

@Serializable
data class THORChainAccountErrorJson(
    @SerialName("message")
    val message: String,
    @SerialName("code")
    val code: Int,
)

@Serializable
data class THORChainAccountValueJson(
    @SerialName("value")
    val value: THORChainAccountValue?,
)

@Serializable
data class THORChainAccountResultJson(
    @SerialName("result")
    val result: THORChainAccountValueJson?,
)

@Serializable
data class THORChainAccountValue(
    @SerialName("address")
    val address: String?,
    @SerialName("account_number")
    val accountNumber: String?,
    @SerialName("sequence")
    val sequence: String?,
)

@Serializable
data class NativeTxFeeRune(
    @SerialName("native_tx_fee_rune")
    val value: String?,
    @SerialName("tns_register_fee_rune")
    val registerFeeRune: String?,
    @SerialName("tns_fee_per_block_rune")
    val feePerBlock: String?,
)