package com.vultisig.wallet.data.repositories.vault

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import javax.inject.Inject

data class TempVaultDto(
    val vault: Vault,
    val email: String,
    val password: String,
    val hint: String?,
)

interface TemporaryVaultRepository {
    fun add(vault: TempVaultDto)
    fun getById(vaultId: VaultId): TempVaultDto
}

internal class TemporaryVaultRepositoryImpl @Inject constructor() : TemporaryVaultRepository {
    private val vaults = mutableMapOf<VaultId, TempVaultDto>()

    override fun add(vault: TempVaultDto) {
        vaults[vault.vault.id] = vault
    }

    override fun getById(vaultId: VaultId): TempVaultDto =
        vaults[vaultId] ?: error("Vault not found")

}