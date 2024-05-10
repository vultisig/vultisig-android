package com.vultisig.wallet.chains

import tss.Tss
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

object PublicKeyHelper {
    fun getDerivedPublicKey(
        hexPublicKey: String,
        hexChainCode: String,
        derivePath: String,
    ): String {
        return Tss.getDerivedPubKey(hexPublicKey, hexChainCode, derivePath, false)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getPublicKey(
        hexPublicKey: String,
        hexChainCode: String,
        coinType: CoinType,
    ): PublicKey {
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            hexPublicKey,
            hexChainCode,
            coinType.derivationPath()
        )
        return PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
    }

}