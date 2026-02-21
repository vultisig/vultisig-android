package com.vultisig.wallet.data.chains.helpers

import tss.Tss

internal object PublicKeyHelper {
    fun getDerivedPublicKey(
        hexPublicKey: String,
        hexChainCode: String,
        derivePath: String,
    ): String {
        // Empty chain code = key is already derived (KeyImport per-chain keys).
        // Skip BIP32 derivation and return the key as-is.
        if (hexChainCode.isEmpty()) return hexPublicKey
        return Tss.getDerivedPubKey(hexPublicKey, hexChainCode, derivePath, false)
    }
}