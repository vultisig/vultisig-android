package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class ReshareMessage(
    @SerializedName("sessionID")
    val sessionID: String,
    @SerializedName("hexChainCode")
    val hexChainCode: String,
    @SerializedName("serviceName")
    val serviceName: String,
    @SerializedName("pubKeyECDSA")
    val pubKeyECDSA: String,
    @SerializedName("oldParties")
    val oldParties: List<String>,
    @SerializedName("encryptionKeyHex")
    val encryptionKeyHex: String,
    @SerializedName("useVultisigRelay")
    val useVultisigRelay: Boolean,
    @SerializedName("oldResharePrefix")
    val oldResharePrefix: String,
)