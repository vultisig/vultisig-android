package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefaultChainsRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

internal interface SaveVaultUseCase : suspend (Vault, Boolean) -> Unit

internal class DuplicateVaultException : IllegalStateException("Vault already exists")

internal class SaveVaultUseCaseImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val defaultChainsRepository: DefaultChainsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : SaveVaultUseCase {
    override suspend fun invoke(vault: Vault, isReshare: Boolean) {
        Timber.d("saveVault(vault = $vault)")
        if (isReshare) {
            // when it is reshare , user already select the chain
            vaultRepository.upsert(vault)
            return
        }
        vaultRepository.getByEcdsa(vault.pubKeyECDSA)?.let {
            Timber.d("saveVault: vault already exists, updating")
            throw DuplicateVaultException()
        }
        vaultRepository.add(vault)

        // if vault has no coins, then add default coins
        if (vault.coins.isEmpty()) {
            Timber.d("saveVault: vault has no coins, adding default coins")

            val vaultId = vault.id

            val insertedVault = vaultRepository.get(vaultId)
                ?: error("Vault didn't save properly")

            val nativeTokens = tokenRepository.nativeTokens.first()
                .associateBy { it.chain }

            defaultChainsRepository.selectedDefaultChains
                .first()
                .mapNotNull { nativeTokens[it] }
                .forEach { nativeToken ->
                    val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                        nativeToken,
                        insertedVault
                    )
                    val updatedNativeToken = nativeToken.copy(
                        address = address,
                        hexPublicKey = derivedPublicKey
                    )
                    vaultRepository.addTokenToVault(vaultId, updatedNativeToken)
                }
        }
    }

}