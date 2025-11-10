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

interface ActiveBondedNodeRepository {
    fun getBondedNodesFlow(vaultId: String): Flow<List<ActiveBondedNode>>

    suspend fun getBondedNodes(vaultId: String): List<ActiveBondedNode>

    suspend fun saveBondedNode(vaultId: String, node: ActiveBondedNode)

    suspend fun saveBondedNodes(vaultId: String, nodes: List<ActiveBondedNode>)

    suspend fun deleteBondedNodes(vaultId: String)

    suspend fun deletedBondedNodeByAddress(vaultId: String, nodeAddress: String)
}

@Singleton
internal class ActiveBondedNodeRepositoryImpl @Inject constructor(
    private val activeBondedNodeDao: ActiveBondedNodeDao
): ActiveBondedNodeRepository {

    override fun getBondedNodesFlow(vaultId: String): Flow<List<ActiveBondedNode>> {
        return activeBondedNodeDao.getAllByVaultId(vaultId)
            .map { entities -> entities.toDomainModels() }
    }

    override suspend fun getBondedNodes(vaultId: String): List<ActiveBondedNode> {
        return activeBondedNodeDao.getAllByVaultIdSuspend(vaultId).toDomainModels()
    }

    override suspend fun saveBondedNode(vaultId: String, node: ActiveBondedNode) {
        activeBondedNodeDao.insert(node.toEntity(vaultId))
    }

    override suspend fun saveBondedNodes(vaultId: String, nodes: List<ActiveBondedNode>) {
        activeBondedNodeDao.insertAll(nodes.toEntities(vaultId))
    }

    override suspend fun deleteBondedNodes(vaultId: String) {
        activeBondedNodeDao.deleteAllByVaultId(vaultId)
    }

    override suspend fun deletedBondedNodeByAddress(vaultId: String, nodeAddress: String) {
        activeBondedNodeDao.deleteByVaultIdAndNodeAddress(vaultId, nodeAddress)
    }
}