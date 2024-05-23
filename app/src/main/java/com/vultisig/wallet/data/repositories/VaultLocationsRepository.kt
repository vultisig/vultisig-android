package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.common.KeyOrderLocation.HOME_SCREEN_VAULT_LIST
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.models.Vault
import javax.inject.Inject


internal interface VaultLocationsRepository {
    suspend fun getVaultsForHomeLocation(): List<Vault>
    suspend fun updateVaultOrderInHomeLocation(newOrderKeys: List<Vault>): List<Vault>
}


internal class VaultLocationsRepositoryImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val orderDB: OrderDB
) : VaultLocationsRepository {

    override suspend fun getVaultsForHomeLocation(): List<Vault> {
        val vaults = vaultRepository.getAll()
        val positions = vaults.map { vault: Vault ->
            orderDB.getPosition(HOME_SCREEN_VAULT_LIST, vault.name)
        }
        return vaults.zip(positions).sortedByDescending { it.second }.map { it.first }
    }


    override suspend fun updateVaultOrderInHomeLocation(newOrderKeys: List<Vault>): List<Vault> {
        orderDB.update(HOME_SCREEN_VAULT_LIST, newOrderKeys.map { it.name }.reversed())
        return getVaultsForHomeLocation()
    }
}