package com.vultisig.wallet.data.chains.helpers

import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey

object MayaChainHelper {

    fun getAddress(publicKey: PublicKey): String =
        AnyAddress(publicKey, CoinType.THORCHAIN, "maya").description()

}