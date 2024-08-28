package com.vultisig.wallet.data.api.models.cosmos

import com.google.gson.annotations.SerializedName

data class THORChainAccountValue(
    @SerializedName("address")
    val address: String?,
    @SerializedName("account_number")
    val accountNumber: String?,
    @SerializedName("sequence")
    val sequence: String?,
)