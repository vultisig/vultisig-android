package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.VaultId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * In-memory, process-lifetime cache of the last state a DeFi detail screen rendered, per vault.
 *
 * The detail view-models are nav-scoped, so popping back to the DeFi list destroys them and the
 * next open would otherwise cold-start: blank cards, header on a spinner, and the enabled set back
 * at its defaults until the store answers. Each screen hands its state over in `onCleared` and
 * seeds from it on the next `setData`, so a re-entry paints what the user last saw while the live
 * read refreshes underneath. This is the same trade the chains that already got it right make —
 * [com.vultisig.wallet.data.repositories.TronDeFiSnapshotCache],
 * [com.vultisig.wallet.data.repositories.CosmosStakingSnapshotCache] — and it deliberately stays in
 * memory: nothing to migrate, nothing to clean up when a vault or coin is deleted, and a restart
 * reads the chain.
 *
 * Entries are keyed by vault *and* snapshot type, so two screens sharing a vault cannot overwrite
 * each other's state, and a read can only ever hand back the type it asked for.
 */
@Singleton
internal class DeFiPositionsSnapshotCache @Inject constructor() {

    private val snapshots = ConcurrentHashMap<String, Any>()

    fun <T : Any> read(vaultId: VaultId, type: KClass<T>): T? =
        type.safeCast(snapshots[key(vaultId, type)])

    fun write(vaultId: VaultId, snapshot: Any) {
        snapshots[key(vaultId, snapshot::class)] = snapshot
    }

    private fun key(vaultId: VaultId, type: KClass<*>): String = "$vaultId:${type.qualifiedName}"
}
