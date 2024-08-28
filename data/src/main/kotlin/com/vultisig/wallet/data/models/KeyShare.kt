package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

data class KeyShare(
    @SerializedName("pubKey")
    val pubKey: String,
    @SerializedName("keyshare")
    val keyShare: String,
)
