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
    /**
     * Initiator opt-in for parallel ECDSA + EdDSA reshare (proto field 10). When `true`, every peer
     * routes through the `/vault/batch/reshare` FastVault endpoint and `p-ecdsa` / `p-eddsa` relay
     * channels — defaults to `false` so a peer that ignores the flag keeps the legacy path.
     */
    val isTssBatch: Boolean = false,
)
