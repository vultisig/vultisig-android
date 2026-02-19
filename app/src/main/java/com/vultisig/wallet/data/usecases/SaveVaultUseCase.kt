package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
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
    override suspend fun invoke(vault: Vault, shouldOverrideVault: Boolean) {
        if (shouldOverrideVault) {
            vaultRepository.upsert(vault)
        } else {
            vaultRepository.getByEcdsa(vault.pubKeyECDSA)?.let {
                Timber.d("saveVault: vault already exists, updating")
                throw DuplicateVaultException()
            }
            vaultRepository.add(vault)
        }
        // if vault has no coins, then add default coins
        if (vault.coins.isEmpty()) {
            Timber.d("saveVault: vault has no coins, adding default coins")

            val vaultId = vault.id

            val insertedVault = vaultRepository.get(vaultId)
                ?: error("Vault didn't save properly")

            val nativeTokens = tokenRepository.nativeTokens.first()
                .associateBy { it.chain }

            // For KeyImport: only add chains the user explicitly selected during import.
            // Each chain's key is already fully derived, so no BIP32 derivation is needed.
            val chainsToAdd = if (insertedVault.libType == SigningLibType.KeyImport) {
                insertedVault.chainPublicKeys
                    .mapNotNull { cpk ->
                        try { Chain.fromRaw(cpk.chain) } catch (_: Exception) { null }
                    }
            } else {
                defaultChainsRepository.selectedDefaultChains.first()
            }

            chainsToAdd
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