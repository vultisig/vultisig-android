package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.common.KeyOrderLocation.HOME_SCREEN_VAULT_LIST
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import javax.inject.Inject

class VaultRepository @Inject constructor(
    private val vaultDB: VaultDB, private val orderDB: OrderDB
) {
    init {
        orderDB.initIndexes(
            HOME_SCREEN_VAULT_LIST, vaultDB.selectAll().map { it.name })
    }

    fun getVaultsForHomeLocation(): List<Vault> {
        val vaults = vaultDB.selectAll()
        val positions = vaults.map { vault: Vault ->
            orderDB.getPosition(HOME_SCREEN_VAULT_LIST, vault.name)
        }
        return vaults.zip(positions).sortedByDescending { it.second }.map { it.first }
    }


    fun updateVaultOrderInHomeLocation(newOrderKeys: List<Vault>): List<Vault> {
        orderDB.update(HOME_SCREEN_VAULT_LIST, newOrderKeys.map { it.name }.reversed())
        return getVaultsForHomeLocation()
    }
}