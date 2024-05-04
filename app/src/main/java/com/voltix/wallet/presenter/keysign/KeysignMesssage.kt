package com.voltix.wallet.presenter.keysign

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.io.Serializable
@Parcelize
data class KeysignMesssage (
    val sessionID: String,
    val serverName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val useVoltixRelay: Boolean
) : Parcelable {
}