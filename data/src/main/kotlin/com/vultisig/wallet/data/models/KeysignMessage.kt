package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.KeysignPayload
import vultisig.keysign.v1.CustomMessagePayload

data class KeysignMessage(
    val sessionID: String,
    val serviceName: String,
    val payload: KeysignPayload?,
    val encryptionKeyHex: String,
    val useVultisigRelay: Boolean,
    val customMessagePayload: CustomMessagePayload?,
)