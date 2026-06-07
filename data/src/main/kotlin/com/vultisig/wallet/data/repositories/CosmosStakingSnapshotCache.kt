package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-known, display-ready staking state for a Cosmos-SDK staking address (LUNA / LUNC). The rows
 * already carry their fiat strings, so a re-entry can render this snapshot verbatim while the live
 * fan-out refreshes in the background.
 */
data class CosmosStakingSnapshot(
    val positions: List<CosmosStakePositionRow>,
    val pendingUnbondings: List<CosmosUnbondingDelegation>,
    val totalStaked: BigDecimal,
    val totalStakedFiat: String,
    val totalAmountPrice: String,
)

/**
 * In-memory, process-lifetime cache of the last-known Cosmos staking snapshot per `chain:address`,
 * so the staking-positions screen renders immediately on reopen and refreshes in the background
 * instead of flashing the empty/zero state. Mirrors [TronDeFiSnapshotCache] and the in-memory query
 * cache the other DeFi chains (and vultisig-ios) already rely on; it deliberately avoids a second
 * on-disk store so nothing has to be cleaned up when a vault or coin is deleted. See issue #4764.
 */
@Singleton
class CosmosStakingSnapshotCache @Inject constructor() {

    private val snapshots = ConcurrentHashMap<String, CosmosStakingSnapshot>()

    fun read(key: String): CosmosStakingSnapshot? = snapshots[key]

    fun write(key: String, snapshot: CosmosStakingSnapshot) {
        snapshots[key] = snapshot
    }
}
