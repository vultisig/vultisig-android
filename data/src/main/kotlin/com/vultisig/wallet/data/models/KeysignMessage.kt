package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.data.models.payload.KeysignPayload


data class KeysignMessage(
    @SerializedName("sessionID")
    val sessionID: String,
    @SerializedName("serviceName")
    val serviceName: String,
    @SerializedName("payload")
    val payload: KeysignPayload,
    @SerializedName("encryptionKeyHex")
    val encryptionKeyHex: String,
    @SerializedName("useVultisigRelay")
    val useVultisigRelay: Boolean,
)