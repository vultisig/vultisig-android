package com.vultisig.wallet.data.repositories.swap

/**
 * LI.FI "Auto" slippage, ported from the SDK's `lifiSlippage.ts` (vultisig-sdk#524).
 *
 * The tolerance is baked into the LI.FI-prebuilt transaction's `minAmountOut` floor at quote time,
 * so the router reverts once the simulated output drops below it. Omitting the `slippage` param
 * leaves LI.FI's own 0.5% default, which is too tight for an MPC wallet: the keysign ceremony adds
 * 30–90s of drift between the quote and the broadcast, and that floor is what produced the
 * `Insufficient output` reverts in #5801.
 *
 * Two tiers:
 * - stable pairs: [STABLE_PAIR_BPS], well above the typical concentrated-liquidity spread (2–5 bps)
 *   while avoiding the 1% MEV surface on tight-peg swaps;
 * - everything else: [DEFAULT_BPS], which covers the ceremony drift. Typical realised slippage is
 *   under 10 bps, so this is a ceiling rather than the expected cost.
 *
 * Cross-chain caveat: the tolerance applies to the final destination amount only. A LI.FI
 * bridge+swap route has a second slippage point at the bridge pool exit that the bridge protocol
 * manages itself, so realised slippage there can exceed this floor.
 *
 * A user-chosen preset or custom value overrides both tiers untouched.
 */
internal object LiFiSlippage {

    /** Auto tolerance for a volatile pair, in basis points (100 bps = 1%). */
    const val DEFAULT_BPS = 100

    /** Auto tolerance for a stable-to-stable pair, in basis points (30 bps = 0.3%). */
    const val STABLE_PAIR_BPS = 30

    /**
     * Combined affiliate + slippage ceiling, in basis points. Logged, never enforced: the
     * integrator fee must not silently compound with a wide tolerance into a >3% effective cost.
     */
    const val MAX_COMBINED_COST_BPS = 300

    /**
     * Tickers that trade within a tight peg. DAI is included because its depth against USDC is
     * comparable to USDT's on most DEXs, so [STABLE_PAIR_BPS] is still safe headroom there.
     */
    val STABLE_TICKERS =
        setOf(
            "USDC",
            "USDT",
            "DAI",
            "BUSD",
            "TUSD",
            "FRAX",
            "USDP",
            "GUSD",
            "LUSD",
            "USDD",
            "FDUSD",
            "PYUSD",
        )

    fun isStablePair(srcTicker: String, dstTicker: String): Boolean =
        isStable(srcTicker) && isStable(dstTicker)

    /**
     * [override] is the user's own tolerance in basis points, or null for "Auto" — in which case
     * the pair picks the tier. Never returns null, so the `slippage` param is always sent.
     */
    fun resolveBps(override: Int?, srcTicker: String, dstTicker: String): Int =
        override ?: if (isStablePair(srcTicker, dstTicker)) STABLE_PAIR_BPS else DEFAULT_BPS

    private fun isStable(ticker: String): Boolean = ticker.uppercase() in STABLE_TICKERS
}
