package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface GetOrderedVaults : suspend (String?, Boolean) -> Flow<List<Vault>>

internal class GetOrderedVaultsImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultOrderRepository: VaultOrderRepository,
): GetOrderedVaults {
    override suspend fun invoke(parentId: String?, getAll: Boolean): Flow<List<Vault>> =
        vaultOrderRepository.loadOrders(parentId).map { orders ->
            val addressAndOrderMap = mutableMapOf<Vault, Float>()
            vaultRepository.getAll().forEach { eachVault ->
                val order = orders.find { it.value == eachVault.id }

                val orderValue = order?.order
                ?: vaultOrderRepository.insert(null, eachVault.id)
                if (getAll || order?.parentId == parentId) {
                    addressAndOrderMap[eachVault] = orderValue
                }
            }
            addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
        }

}