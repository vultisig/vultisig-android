package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.crypto.Ed25519ScalarUtil
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.ChainImportSetting
import com.vultisig.wallet.data.repositories.DerivationPath
import wallet.core.jni.Derivation
import wallet.core.jni.HDWallet
import javax.inject.Inject

data class ChainKeyResult(
    val chain: Chain,
    val privateKeyHex: String,
    val isEddsa: Boolean,
)

fun interface DeriveChainKeyUseCase {
    operator fun invoke(
        mnemonic: String,
        chainSetting: ChainImportSetting,
    ): ChainKeyResult
}

internal class DeriveChainKeyUseCaseImpl @Inject constructor() : DeriveChainKeyUseCase {

    override fun invoke(
        mnemonic: String,
        chainSetting: ChainImportSetting,
    ): ChainKeyResult {
        val wallet = HDWallet(mnemonic, "")
        val chain = chainSetting.chain
        val isEddsa = chain.TssKeysignType == TssKeyType.EDDSA

        val keyData = when {
            chain == Chain.Solana && chainSetting.derivationPath == DerivationPath.Phantom ->
                wallet.getKeyDerivation(chain.coinType, Derivation.SOLANASOLANA).data()

            else -> wallet.getKeyForCoin(chain.coinType).data()
        }

        val privateKeyHex = if (isEddsa) {
            // EdDSA chains require scalar clamping before Schnorr keygen (matches iOS)
            Ed25519ScalarUtil.clampThenUniformScalar(keyData)
                .joinToString("") { "%02x".format(it) }
        } else {
            keyData.joinToString("") { "%02x".format(it) }
        }

        return ChainKeyResult(
            chain = chain,
            privateKeyHex = privateKeyHex,
            isEddsa = isEddsa,
        )
    }
}
