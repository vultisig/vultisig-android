package com.vultisig.wallet.presenter.keysign

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class KeysignMesssage(
    val sessionID: String,
    val serviceName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val usevultisigRelay: Boolean,
) : Parcelable