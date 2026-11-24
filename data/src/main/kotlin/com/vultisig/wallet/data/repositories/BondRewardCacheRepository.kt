package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.data.usecases.BondChurnReward
import com.vultisig.wallet.data.usecases.BondRewardHistoryUseCase
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * The churn rewards a bond provider has already been paid on a node, kept on the device.
 *
 * A past churn's reward never changes, so it is read from the network once. Nodes keep only recent
 * state, so this copy is also the only source for churns they have since pruned. At most
 * [BondRewardHistoryUseCase.MAX_CHURNS] rows per chain, node and bond address, newest kept.
 *
 * Best effort: a storage failure is logged and reads as nothing saved, so it never costs the
 * rewards the network did return.
 */
@Singleton
class BondRewardCacheRepository @Inject constructor(private val dataStore: AppDataStore) {

    /** Saved rewards by churn height. */
    suspend fun getRewards(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): Map<Long, BondChurnReward> =
        try {
            decodeAll(
                dataStore.readData(rewardsKey(chain, nodeAddress, bondAddress)).first().orEmpty()
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to read saved bond rewards for %s", nodeAddress)
            emptyMap()
        }

    /** Merges [rewards] into what is saved now, so two loads of one node keep each other's rows. */
    suspend fun saveRewards(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        rewards: Collection<BondChurnReward>,
    ) {
        if (rewards.isEmpty()) return
        val key = rewardsKey(chain, nodeAddress, bondAddress)
        try {
            dataStore.editData { preferences ->
                val merged =
                    decodeAll(preferences[key].orEmpty()) + rewards.associateBy { it.churnHeight }
                preferences[key] =
                    merged.values
                        .sortedByDescending { it.churnHeight }
                        .take(BondRewardHistoryUseCase.MAX_CHURNS)
                        .map(::encode)
                        .toSet()
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to save bond rewards for %s", nodeAddress)
        }
    }

    private companion object {
        const val SEPARATOR = ":"

        fun rewardsKey(chain: Chain, nodeAddress: String, bondAddress: String) =
            stringSetPreferencesKey("bond_rewards_${chain.raw}_${nodeAddress}_$bondAddress")

        fun encode(reward: BondChurnReward) =
            listOf(reward.churnHeight, reward.date.time, reward.amount).joinToString(SEPARATOR)

        fun decodeAll(stored: Set<String>): Map<Long, BondChurnReward> =
            stored.mapNotNull(::decode).associateBy { it.churnHeight }

        /** A malformed entry is dropped, so its churn is read from the node again. */
        fun decode(entry: String): BondChurnReward? {
            val parts = entry.split(SEPARATOR)
            if (parts.size != 3) return null
            val height = parts[0].toLongOrNull() ?: return null
            val millis = parts[1].toLongOrNull() ?: return null
            val amount = parts[2].toBigIntegerOrNull()?.takeIf { it.signum() >= 0 } ?: return null
            return BondChurnReward(churnHeight = height, date = Date(millis), amount = amount)
        }
    }
}
