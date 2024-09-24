package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.KeysignPayload

data class KeysignMessage(
    val sessionID: String,
    val serviceName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val useVultisigRelay: Boolean,
)