package com.vultisig.wallet.data.repositories.vault

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.utils.TransientStore
import javax.inject.Inject

class TempVaultDto(
    val vault: Vault,
    val email: String,
    val password: String,
    val hint: String?,
)

interface TemporaryVaultRepository {
    fun add(vault: TempVaultDto)

    fun findById(vaultId: VaultId): TempVaultDto?

    fun remove(vaultId: VaultId)
}

internal class TemporaryVaultRepositoryImpl @Inject constructor() : TemporaryVaultRepository {
    private val vaults = TransientStore<VaultId, TempVaultDto>(capacity = 4, lifetimeMillis = 900_000)

    override fun add(vault: TempVaultDto) {
        vaults.put(vault.vault.id, vault)
    }

    override fun findById(vaultId: VaultId): TempVaultDto? = vaults.get(vaultId)

    override fun remove(vaultId: VaultId) {
        vaults.remove(vaultId)
    }
}
