@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.VaultRepository
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import javax.inject.Inject

fun interface CheckMnemonicDuplicateUseCase {
    suspend operator fun invoke(mnemonic: String): Boolean
}

internal class CheckMnemonicDuplicateUseCaseImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
) : CheckMnemonicDuplicateUseCase {

    override suspend fun invoke(mnemonic: String): Boolean {
        val wallet = HDWallet(mnemonic, "")
        val ecdsaPubKey = wallet.getMasterKey(Curve.SECP256K1)
            .getPublicKeySecp256k1(true)
            .data()
            .toHexString()

        return vaultRepository.getByEcdsa(ecdsaPubKey) != null
    }
}
