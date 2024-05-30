package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class KeygenMessage(
    @SerializedName("vaultName")
    val vaultName: String,
    @SerializedName("sessionID")
    val sessionID: String,
    @SerializedName("hexChainCode")
    val hexChainCode: String,
    @SerializedName("serviceName")
    val serviceName: String,
    @SerializedName("encryptionKeyHex")
    val encryptionKeyHex: String,
    @SerializedName("useVultisigRelay")
    val useVultisigRelay: Boolean,
)