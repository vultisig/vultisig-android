package com.vultisig.wallet.presenter.keysign

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class KeysignMesssage (
    val sessionID: String,
    val serverName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val usevultisigRelay: Boolean
) : Parcelable {
}