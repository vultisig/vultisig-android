package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeysignResponseSerializable(
    @SerialName("Msg")
    val msg: String,
    @SerialName("R")
    val r: String,
    @SerialName("S")
    val s: String,
    @SerialName("DerSignature")
    val derSignature: String,
    @SerialName("RecoveryID")
    val recoveryID: String,
) {
    fun toOriginal() = tss.KeysignResponse().apply {
        msg = this@KeysignResponseSerializable.msg
        r = this@KeysignResponseSerializable.r
        s = this@KeysignResponseSerializable.s
        derSignature = this@KeysignResponseSerializable.derSignature
        recoveryID = this@KeysignResponseSerializable.recoveryID
    }

    companion object {
        fun serialize(response: tss.KeysignResponse) = KeysignResponseSerializable(
            msg = response.msg,
            r = response.r,
            s = response.s,
            derSignature = response.derSignature,
            recoveryID = response.recoveryID,
        )
    }
}
