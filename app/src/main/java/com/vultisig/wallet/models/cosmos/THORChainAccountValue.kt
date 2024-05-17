package com.vultisig.wallet.models.cosmos

import com.google.gson.annotations.SerializedName

data class THORChainAccountValue(
    val address: String?,
    @SerializedName("account_number") val accountNumber: String?,
    val sequence: String?,
) {
}