package com.voltix.wallet.models

data class ReshareMessage(
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val pubKeyECDSA: String,
    val oldParties: List<String>,
    val encryptionKeyHex: String,
    val useVoltixRelay: Boolean,
) {

}
