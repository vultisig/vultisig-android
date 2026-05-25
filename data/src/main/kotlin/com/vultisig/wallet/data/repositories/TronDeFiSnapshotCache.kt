package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Last-known account + resource state for a TRON address. */
data class TronDeFiSnapshot(val account: TronAccountJson, val resource: TronAccountResourceJson)

/**
 * In-memory, process-lifetime cache of the last-known TRON DeFi snapshot per address, so the DeFi
 * positions screen renders immediately on reopen and refreshes in the background instead of
 * flashing skeletons. This mirrors the in-memory query cache the other chains (and vultisig-ios)
 * already rely on; it deliberately avoids a second on-disk store next to the frozen total persisted
 * through [StakingDetailsRepository], so the two can't drift on disk and nothing has to be cleaned
 * up when a vault or coin is deleted. See issue #4568.
 */
@Singleton
class TronDeFiSnapshotCache @Inject constructor() {

    private val snapshots = ConcurrentHashMap<String, TronDeFiSnapshot>()

    fun read(tronAddress: String): TronDeFiSnapshot? = snapshots[tronAddress]

    fun write(tronAddress: String, snapshot: TronDeFiSnapshot) {
        snapshots[tronAddress] = snapshot
    }
}
