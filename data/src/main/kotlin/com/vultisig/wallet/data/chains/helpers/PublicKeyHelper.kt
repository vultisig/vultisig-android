package com.vultisig.wallet.data.chains.helpers

import tss.Tss

internal object PublicKeyHelper {
    fun getDerivedPublicKey(
        hexPublicKey: String,
        hexChainCode: String,
        derivePath: String,
    ): String {
        return Tss.getDerivedPubKey(hexPublicKey, hexChainCode, derivePath, false)
    }

}