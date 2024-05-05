package com.vultisig.wallet.chains

import tss.Tss

object PublicKeyHelper {
    fun getDerivedPublicKey(
        hexPublicKey: String,
        hexChainCode: String,
        derivePath: String,
    ): String {
        return Tss.getDerivedPubKey(hexPublicKey, hexChainCode, derivePath, false)
    }
}