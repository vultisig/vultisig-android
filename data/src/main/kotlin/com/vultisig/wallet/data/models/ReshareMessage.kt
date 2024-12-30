package com.vultisig.wallet.data.models

data class ReshareMessage(
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val pubKeyECDSA: String,
    val oldParties: List<String>,
    val encryptionKeyHex: String,
    val useVultisigRelay: Boolean,
    val oldResharePrefix: String,
    val vaultName: String,
    val libType: SigningLibType,
)