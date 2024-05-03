package com.voltix.wallet.presenter.keysign

data class KeysignMesssage(
    val sessionID: String,
    val serverName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val useVoltixRelay: Boolean
) {
}