package com.vultisig.wallet.models.cosmos

import com.google.gson.annotations.SerializedName

internal data class THORChainAccountValue(
    @SerializedName("address")
    val address: String?,
    @SerializedName("account_number")
    val accountNumber: String?,
    @SerializedName("sequence")
    val sequence: String?,
)