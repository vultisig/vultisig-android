package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.payload.KeysignPayload

/**
 * Fetches and caches Blockaid scan results for the dApp signing hero.
 *
 * Caching is the load-bearing feature: a single scan underpins both the balance-change hero and the
 * security badge across the verify → sign → done screens. Each screen owns a separate ViewModel, so
 * without a cache they would each re-hit Blockaid's API and risk showing inconsistent data after
 * the user already committed to signing.
 *
 * Implementations MUST coalesce concurrent calls for the same key and cache successful scans
 * (including empty results). Transient failures are NOT cached and are surfaced to the caller as
 * [BlockaidKeysignScanResult.EMPTY] so the next screen entry triggers a fresh scan rather than
 * propagating the failure into the signing flow.
 */
interface BlockaidSimulationService {

    /**
     * Returns the simulation + scanner result for the given payload, hitting the Blockaid API only
     * on cache miss. Returns [BlockaidKeysignScanResult.EMPTY] when the chain is unsupported, the
     * payload has no calldata to simulate, or the scan failed (failures are not cached).
     */
    suspend fun scan(payload: KeysignPayload): BlockaidKeysignScanResult

    /**
     * Test/diagnostic helper. Drops the in-memory cache so the next [scan] call refreshes from the
     * network. Production code does not invalidate intentionally — the cache is process-lifetime by
     * design. Suspends so the call can take the same internal lock that [scan] holds, preventing a
     * race against an in-flight scan.
     */
    suspend fun invalidateAll()
}
