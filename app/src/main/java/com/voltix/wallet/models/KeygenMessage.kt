package com.voltix.wallet.models

data class KeygenMessage(
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val encryptionKeyHex: String,
    val useVoltixRelay: Boolean,
) {

}