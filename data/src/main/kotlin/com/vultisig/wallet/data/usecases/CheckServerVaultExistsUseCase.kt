@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet

fun interface CheckServerVaultExistsUseCase {
    suspend operator fun invoke(mnemonic: String): Boolean
}

internal class CheckServerVaultExistsUseCaseImpl
@Inject
constructor(private val vultiSignerRepository: VultiSignerRepository) :
    CheckServerVaultExistsUseCase {

    override suspend fun invoke(mnemonic: String): Boolean {
        val wallet = HDWallet(mnemonic, "")
        val masterKey =
            checkNotNull(wallet.getMasterKey(Curve.SECP256K1)) {
                "SECP256K1 master key derivation failed"
            }
        val ecdsaPubKey =
            checkNotNull(masterKey.getPublicKeySecp256k1(true)) { "Public key derivation failed" }
                .data()
                .toHexString()

        return vultiSignerRepository.hasFastSign(ecdsaPubKey)
    }
}
