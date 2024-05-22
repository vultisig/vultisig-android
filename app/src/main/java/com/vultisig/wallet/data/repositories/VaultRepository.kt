package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.common.KeyOrderLocation.HOME_SCREEN_VAULT_LIST
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import javax.inject.Inject


internal interface VaultRepository {
    fun getVaultsForHomeLocation(): List<Vault>
    fun updateVaultOrderInHomeLocation(newOrderKeys: List<Vault>): List<Vault>
}


internal class VaultRepositoryImpl @Inject constructor(
    private val vaultDB: VaultDB, private val orderDB: OrderDB
) : VaultRepository {
    init {
        orderDB.initIndexes(
            HOME_SCREEN_VAULT_LIST, vaultDB.selectAll().map { it.name })
    }

    override fun getVaultsForHomeLocation(): List<Vault> {
        val vaults = vaultDB.selectAll()
        val positions = vaults.map { vault: Vault ->
            orderDB.getPosition(HOME_SCREEN_VAULT_LIST, vault.name)
        }
        return vaults.zip(positions).sortedByDescending { it.second }.map { it.first }
    }


    override fun updateVaultOrderInHomeLocation(newOrderKeys: List<Vault>): List<Vault> {
        orderDB.update(HOME_SCREEN_VAULT_LIST, newOrderKeys.map { it.name }.reversed())
        return getVaultsForHomeLocation()
    }
}