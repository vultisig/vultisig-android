package com.vultisig.wallet.data.db.mappers

import com.vultisig.wallet.data.db.models.ActiveBondedNodeEntity
import com.vultisig.wallet.data.db.models.BondedNodeEntity
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import java.math.BigInteger

fun ActiveBondedNode.toEntity(vaultId: String): ActiveBondedNodeEntity {
    return ActiveBondedNodeEntity(
        id = this.id,
        node = BondedNodeEntity(
            address = this.node.address,
            state = this.node.state
        ),
        amount = this.amount.toString(),
        coinId = this.coinId,
        apy = this.apy,
        nextReward = this.nextReward,
        nextChurn = this.nextChurn,
        vaultId = vaultId,
    )
}

fun ActiveBondedNodeEntity.toDomainModel(): ActiveBondedNode {
    return ActiveBondedNode(
        id = this.id,
        node = ActiveBondedNode.BondedNode(
            address = this.node.address,
            state = this.node.state
        ),
        amount = this.amount.toBigIntegerOrNull() ?: BigInteger.ZERO,
        coinId = this.coinId,
        apy = this.apy,
        nextReward = this.nextReward,
        nextChurn = this.nextChurn
    )
}

fun List<ActiveBondedNodeEntity>.toDomainModels(): List<ActiveBondedNode> {
    return this.map { it.toDomainModel() }
}

fun List<ActiveBondedNode>.toEntities(vaultId: String): List<ActiveBondedNodeEntity> {
    return this.map { it.toEntity(vaultId) }
}