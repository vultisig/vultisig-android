package com.vultisig.wallet.data.db.mappers

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.db.models.StakingDetailsEntity
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger
import timber.log.Timber

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

fun StakingDetailsEntity.toDomainModel(): StakingDetails =
    toDomainModelOrNull() ?: error("Coin not found for id: ${this.coinId}")

private fun StakingDetailsEntity.toDomainModelOrNull(): StakingDetails? {
    val coins = Coins.allResolvable

    val coin = coins.find { it.id == this.coinId } ?: return null

    val rewardsCoin =
        this.rewardsCoinId?.let { rewardsCoinId -> coins.find { it.id == rewardsCoinId } }

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

/**
 * Drops rows whose coin id no longer resolves instead of failing the whole read: these entities are
 * shared by every staking service on the vault, so one unknown coin must not take the DeFi tab down
 * with it.
 */
fun List<StakingDetailsEntity>.toDomainModels(): List<StakingDetails> = mapNotNull { entity ->
    entity.toDomainModelOrNull().also {
        if (it == null) {
            Timber.w("Dropping cached staking position for unknown coin id %s", entity.coinId)
        }
    }
}
