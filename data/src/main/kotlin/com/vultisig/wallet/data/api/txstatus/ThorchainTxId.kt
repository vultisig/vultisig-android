package com.vultisig.wallet.data.api.txstatus

/**
 * A broadcast transaction hash re-expressed the way THORChain keys it: uppercase hex with NO `0x`
 * prefix.
 *
 * THORChain- and Cosmos-native hashes already satisfy that, which is why they matched as-is. EVM
 * hashes do not: they are broadcast — and therefore stored — as `0x`-prefixed lowercase, so both
 * `queue/limit_swaps` (whose `swap.tx.id` is the bare form) and Midgard's `?txid=` lookup silently
 * fail to match one. The order is then never seen resting, Cancel stays blocked on
 * `PlacementNotObserved` forever, and no outcome ever resolves.
 *
 * Idempotent for an already-normalized hash — there is no `0x` to strip and uppercasing hex is a
 * no-op — so it is correct to apply on every path, native or EVM. Mirrors iOS's
 * `THORChainTransactionStatusAPI.midgardTxid`.
 */
fun thorchainTxId(hash: String): String =
    hash.trim().removePrefix("0x").removePrefix("0X").uppercase()
