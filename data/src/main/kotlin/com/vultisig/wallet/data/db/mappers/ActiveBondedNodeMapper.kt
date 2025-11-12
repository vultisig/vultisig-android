package com.vultisig.wallet.data.db.mappers

import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.db.models.ActiveBondedNodeEntity
import com.vultisig.wallet.data.db.models.BondedNodeEntity
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger

fun BondedNodePosition.toEntity(vaultId: String): ActiveBondedNodeEntity {
    return ActiveBondedNodeEntity(
        id = this.id,
        node = BondedNodeEntity(
            address = this.node.address,
            state = this.node.state
        ),
        amount = this.amount.toString(),
        coinId = coin.id,
        apy = this.apy,
        nextReward = this.nextReward,
        nextChurn = this.nextChurn,
        vaultId = vaultId,
    )
}

fun ActiveBondedNodeEntity.toDomainModel(): BondedNodePosition {
    return BondedNodePosition(
        id = this.id,
        node = BondedNodePosition.BondedNode(
            address = this.node.address,
            state = this.node.state
        ),
        amount = this.amount.toBigIntegerOrNull() ?: BigInteger.ZERO,
        coin = Coins.ThorChain.RUNE,
        apy = this.apy,
        nextReward = this.nextReward,
        nextChurn = this.nextChurn
    )
}

fun List<ActiveBondedNodeEntity>.toDomainModels(): List<BondedNodePosition> {
    return this.map { it.toDomainModel() }
}

fun List<BondedNodePosition>.toEntities(vaultId: String): List<ActiveBondedNodeEntity> {
    return this.map { it.toEntity(vaultId) }
}