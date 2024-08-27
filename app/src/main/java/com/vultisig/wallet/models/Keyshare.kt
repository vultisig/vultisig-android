package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class KeyShare(
    @SerializedName("pubKey")
    val pubKey: String,
    @SerializedName("keyshare")
    val keyShare: String,
)
