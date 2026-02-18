@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.crypto.Ed25519ScalarUtil
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class MasterKeys(
    val ecdsaMasterKeyHex: String,
    val eddsaMasterKeyHex: String,
    val hexChainCode: String,
)

fun interface ExtractMasterKeysUseCase {
    operator fun invoke(mnemonic: String): MasterKeys
}

/**
 * Extracts root ECDSA/EdDSA master keys and BIP32 chain code from a mnemonic.
 * The chain code is critical: it must be preserved (not overwritten by DKLS output)
 * for correct BIP32 address derivation on chains not imported per-chain.
 */
internal class ExtractMasterKeysUseCaseImpl @Inject constructor() : ExtractMasterKeysUseCase {

    override fun invoke(mnemonic: String): MasterKeys {
        val wallet = HDWallet(mnemonic, "")

        val ecdsaMasterKey = wallet.getMasterKey(Curve.SECP256K1).data()
        val eddsaSeed = wallet.getMasterKey(Curve.ED25519).data()
        val eddsaMasterKey = Ed25519ScalarUtil.clampThenUniformScalar(eddsaSeed)

        // Chain code: HMAC-SHA512("Bitcoin seed", wallet seed) -> bytes [32:64]
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("Bitcoin seed".toByteArray(), "HmacSHA512"))
        val hmacResult = mac.doFinal(wallet.seed())
        val chainCode = hmacResult.sliceArray(32 until 64)

        return MasterKeys(
            ecdsaMasterKeyHex = ecdsaMasterKey.toHexString(),
            eddsaMasterKeyHex = eddsaMasterKey.toHexString(),
            hexChainCode = chainCode.toHexString(),
        )
    }
}
