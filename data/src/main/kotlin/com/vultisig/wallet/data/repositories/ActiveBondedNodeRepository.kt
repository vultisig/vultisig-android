package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.ActiveBondedNodeDao
import com.vultisig.wallet.data.db.mappers.toDomainModels
import com.vultisig.wallet.data.db.mappers.toEntities
import com.vultisig.wallet.data.db.mappers.toEntity
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveBondedNodeRepository @Inject constructor(
    private val activeBondedNodeDao: ActiveBondedNodeDao
) {
    
    fun getBondedNodesFlow(vaultId: String): Flow<List<ActiveBondedNode>> {
        return activeBondedNodeDao.getAllByVaultId(vaultId)
            .map { entities -> entities.toDomainModels() }
    }
    
    suspend fun getBondedNodes(vaultId: String): List<ActiveBondedNode> {
        return activeBondedNodeDao.getAllByVaultIdSuspend(vaultId).toDomainModels()
    }
    
    suspend fun saveBondedNode(vaultId: String, node: ActiveBondedNode) {
        activeBondedNodeDao.insert(node.toEntity(vaultId))
    }
    
    suspend fun saveBondedNodes(vaultId: String, nodes: List<ActiveBondedNode>) {
        activeBondedNodeDao.insertAll(nodes.toEntities(vaultId))
    }

    suspend fun getTotalBondedAmount(vaultId: String): Long? {
        return activeBondedNodeDao.getTotalBondedAmount(vaultId)
    }
}