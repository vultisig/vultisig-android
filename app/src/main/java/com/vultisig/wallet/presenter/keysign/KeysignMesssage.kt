package com.vultisig.wallet.presenter.keysign

import com.google.gson.annotations.SerializedName


internal data class KeysignMesssage(
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