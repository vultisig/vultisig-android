package com.vultisig.wallet.data.usecases

/**
 * How a node spreads its pending-liquidity cleanup across the `PendingLiquidityAgeLimit` cycle.
 *
 * Both nodes collect, in pool-store order, the non-synthetic Available/Staged pools that hold any
 * pending inbound, and give pool `i` a cleanup slot at `i * (ageLimit / poolCount)` blocks into
 * each cycle. A half-deposit is committed alone only on a slot of its pool, and only once it is
 * strictly older than the age limit.
 */
internal enum class PendingLiquidityCleanupSchedule {
    /**
     * thornode: pool `i` is cleaned when `height % ageLimit == (separator * i) % ageLimit`, with
     * `separator` floored at one.
     */
    THORChain,

    /**
     * mayanode: pool `0` is cleaned when `height % ageLimit == 0`; pool `i > 0` whenever `height %
     * ageLimit` is a multiple of `separator * i` — which includes `0`, so every pool is also
     * cleaned at the start of each cycle.
     */
    MayaChain,
}

/**
 * Blocks from [currentHeight] until the node will next try to commit a half-deposit on [pool] on
 * its own, or `null` when [pool] is not one the node cleans.
 *
 * [cleanedPools] are the pools the node would clean, as `/pools` reports them now; their sort order
 * is the node's store order (`pool/<ASSET>`, uppercased). The set moves as other pools gain or lose
 * pending liquidity, and halted chains or a TVL cap skip a slot, so this is an estimate that a
 * re-scan refreshes — never a promise.
 */
internal fun blocksUntilPendingLiquidityCommit(
    schedule: PendingLiquidityCleanupSchedule,
    pool: String,
    cleanedPools: List<String>,
    lastAddHeight: Long,
    ageLimit: Long,
    currentHeight: Long,
): Long? {
    if (ageLimit <= 0) return null
    val ordered = cleanedPools.map { it.uppercase() }.distinct().sorted()
    val index = ordered.indexOf(pool.uppercase())
    if (index < 0) return null

    val earliest = maxOf(lastAddHeight + ageLimit + 1, currentHeight + 1)
    val cycleStart = earliest - Math.floorMod(earliest, ageLimit)
    val offset = earliest - cycleStart
    val separator = ageLimit / ordered.size

    val slotOffset =
        when (schedule) {
            PendingLiquidityCleanupSchedule.THORChain -> {
                val target = Math.floorMod(maxOf(separator, 1L) * index, ageLimit)
                if (target >= offset) target else ageLimit + target
            }
            PendingLiquidityCleanupSchedule.MayaChain -> {
                val step = separator * index
                if (step == 0L) {
                    if (offset == 0L) 0L else ageLimit
                } else {
                    val next = (offset + step - 1) / step * step
                    if (next < ageLimit) next else ageLimit
                }
            }
        }
    return cycleStart + slotOffset - currentHeight
}

/**
 * Whether a node's cleanup pass would visit [asset]'s pool, judged from `/pools` alone.
 *
 * Synthetic pools are always skipped; [derivedChainPrefix] names the chain whose non-native pools
 * are skipped too (thornode's derived `THOR.*` assets), `null` when the node skips none. A missing
 * [status] counts as live: the pool still holds pending liquidity, so it was live when it took it.
 */
internal fun isCleanedPendingLiquidityPool(
    asset: String,
    status: String?,
    pendingInboundNative: String,
    pendingInboundAsset: String,
    derivedChainPrefix: String?,
): Boolean {
    if ('/' in asset) return false
    if (derivedChainPrefix != null && asset.startsWith("$derivedChainPrefix.", ignoreCase = true))
        return false
    val live =
        status == null ||
            status.equals("Available", ignoreCase = true) ||
            status.equals("Staged", ignoreCase = true)
    if (!live) return false
    return (pendingInboundNative.toBigIntegerOrNull()?.signum() ?: 0) > 0 ||
        (pendingInboundAsset.toBigIntegerOrNull()?.signum() ?: 0) > 0
}
