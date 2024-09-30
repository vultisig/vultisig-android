package com.vultisig.wallet.data.chains.helpers

import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

@OptIn(ExperimentalStdlibApi::class)
class MayaChainHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    private val coinType: CoinType = CoinType.THORCHAIN

    fun getAddress(): String {
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = AnyAddress(publicKey, coinType, "maya")
        return address.description()
    }

}