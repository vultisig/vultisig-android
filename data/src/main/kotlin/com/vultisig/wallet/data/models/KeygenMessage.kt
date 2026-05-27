package com.vultisig.wallet.data.models

data class KeygenMessage(
    val vaultName: String,
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val encryptionKeyHex: String,
    val useVultisigRelay: Boolean,
    val libType: SigningLibType,
    val chains: List<String> = emptyList(),
    // Initiator's opt-in to the batched (parallel ECDSA+EdDSA) keygen / key-import protocol.
    // A joiner must follow the initiator's choice so both poll the same relay namespaces.
    val isTssBatch: Boolean = false,
)
