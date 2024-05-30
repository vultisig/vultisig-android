package com.vultisig.wallet.chains

import com.google.gson.annotations.SerializedName

internal data class UtxoInfo(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("amount")
    val amount: ULong,
    @SerializedName("index")
    val index: UInt,
)