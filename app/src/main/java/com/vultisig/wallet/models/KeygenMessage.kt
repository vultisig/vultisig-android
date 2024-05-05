package com.vultisig.wallet.models

data class KeygenMessage(
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val encryptionKeyHex: String,
    val usevultisigRelay: Boolean,
) {

}