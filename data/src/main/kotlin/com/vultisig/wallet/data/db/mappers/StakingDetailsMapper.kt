package com.vultisig.wallet.data.db.mappers

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.db.models.StakingDetailsEntity
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger

fun StakingDetails.toEntity(vaultId: String): StakingDetailsEntity {
    return StakingDetailsEntity(
        id = this.id,
        vaultId = vaultId,
        coinId = this.coin.id,
        stakeAmount = this.stakeAmount.toString(),
        apr = this.apr,
        estimatedRewards = this.estimatedRewards?.toPlainString(),
        nextPayoutDate = this.nextPayoutDate,
        rewards = this.rewards?.toPlainString(),
        rewardsCoinId = this.rewardsCoin?.id,
    )
}

fun StakingDetailsEntity.toDomainModel(): StakingDetails {
    val coins = Coins.all

    val coin = coins.find { it.id == this.coinId }
        ?: throw IllegalStateException("Coin not found for id: ${this.coinId}")
    
    val rewardsCoin = this.rewardsCoinId?.let { rewardsCoinId ->
        coins.find { it.id == rewardsCoinId }
    }
    
    return StakingDetails(
        id = this.id,
        coin = coin,
        stakeAmount = this.stakeAmount.toBigIntegerOrNull() ?: BigInteger.ZERO,
        apr = this.apr,
        estimatedRewards = this.estimatedRewards?.toBigDecimalOrNull(),
        nextPayoutDate = this.nextPayoutDate,
        rewards = this.rewards?.toBigDecimalOrNull(),
        rewardsCoin = rewardsCoin,
    )
}

fun List<StakingDetailsEntity>.toDomainModels(): List<StakingDetails> {
    return this.map { it.toDomainModel() }
}