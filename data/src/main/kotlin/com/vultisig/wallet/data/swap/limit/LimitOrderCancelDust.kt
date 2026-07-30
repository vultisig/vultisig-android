package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.getDustThreshold
import java.math.BigDecimal
import java.math.BigInteger

/**
 * How much to attach to a cancel sent FROM an L1 chain.
 *
 * A `m=<` memo reaches THORChain from a non-THORChain source as a dust transfer to the Asgard
 * inbound vault, observed by Bifrost and dispatched to the same modify handler a native
 * `MsgDeposit` reaches. Pure and fails closed, because the failure mode is silent: an under-funded
 * cancel is dropped by Bifrost before it becomes a `MsgObservedTxIn`, so the transaction confirms
 * on the source chain, the fee is spent, and THORChain never learns it happened.
 */

/**
 * Safety multiple applied over the larger of the two floors.
 *
 * A cancel sitting exactly ON a threshold is a coin-flip: THORNode's own comparisons are not
 * uniformly `>=`, and the published threshold can move between the inbound fetch and the
 * transaction actually landing. Doubling removes both without a magic absolute floor that would be
 * wrong on some chain's units (10,000 is dust on ETH and about $10 on BTC).
 *
 * The cost is real and lands on the user: everything attached to an `m=<` is donated to the pool
 * with no refund path, so doubling doubles that donation. It is bounded by the dust amount itself
 * and disclosed on the confirmation screen.
 */
// `BigInteger.TWO` is API 33; this module ships to minSdk 26.
private val DUST_SAFETY_MULTIPLE: BigInteger = BigInteger.valueOf(2)

/** Failure modes of [limitOrderCancelDustAmount]. Each one means there is nothing safe to sign. */
sealed class LimitOrderCancelDustError(message: String) : IllegalStateException(message) {
    /**
     * THORChain's `inbound_addresses` row carried no `dust_threshold` for this chain, so the
     * minimum Bifrost will actually observe is unknown. Deliberately fatal rather than defaulted:
     * guessing low means the cancel is silently ignored, and guessing high donates more than
     * necessary.
     */
    class ThresholdUnavailable(chain: String) :
        LimitOrderCancelDustError("THORChain published no dust_threshold for $chain")

    class MalformedThreshold(chain: String, value: String) :
        LimitOrderCancelDustError(
            "THORChain published a malformed dust_threshold for $chain: $value"
        )

    class UnusablePrecision(chain: String, decimals: Int) :
        LimitOrderCancelDustError("$chain declares an unusable precision of $decimals decimals")

    /**
     * The computed dust exceeded what this chain could plausibly require. `dust_threshold` is a
     * REMOTE value that directly decides how much of the user's money is irreversibly donated, so
     * this is the one upper bound in the file — every other floor here is a lower one.
     */
    class ExceedsCeiling(chain: String, computed: BigInteger, ceiling: BigInteger) :
        LimitOrderCancelDustError(
            "cancel dust for $chain is $computed, above the $ceiling this chain could plausibly require"
        )

    /**
     * The computed dust is too small for THORChain to observe at all — below the point where
     * `ConvertAmount` truncates it to zero on the way into THORChain's 1e8 accounting.
     *
     * Refusing is deliberately preferred over quietly raising the amount: a value that lands here
     * means the pipeline that produced it is wrong, and bumping it to the bare observable minimum
     * would still be under whatever THORChain actually requires — the same silent failure, one
     * order of magnitude up.
     */
    class BelowObservableMinimum(chain: String, computed: BigInteger, minimum: BigInteger) :
        LimitOrderCancelDustError(
            "cancel dust for $chain is $computed, below the $minimum THORChain can observe"
        )
}

/**
 * The amount to attach to an L1-originated cancel, in the source chain's smallest units.
 *
 * Two independent floors have to be cleared and they are enforced by different systems:
 * - **The signer's own dust floor** ([Chain.getDustThreshold], UTXO chains only) — local. An output
 *   below it is refused before anything is broadcast.
 * - **THORChain's `dust_threshold`** — remote. Bifrost ignores an inbound below it, so the
 *   transaction confirms on the source chain and THORChain never sees it. This is the dangerous
 *   one: it looks exactly like success.
 *
 * The two are quoted in different unit systems. THORChain's is in ITS 1e8 fixed point on every
 * chain, whatever precision that chain uses; the local floor is already in the chain's own smallest
 * units. They are only comparable after [chainSmallestUnitsFromThorchainBaseUnits] — on the
 * 8-decimal UTXO chains the conversion is the identity, which is exactly why reading the threshold
 * as if it were already native is a bug BTC, LTC and DOGE cannot show and an 18-decimal chain can.
 *
 * @param decimals the SOURCE COIN's own precision. Load-bearing, not cosmetic: it is the entire
 *   difference between 2e13 wei and 2000 wei.
 */
fun limitOrderCancelDustAmount(
    localDustFloor: BigInteger,
    inboundDustThreshold: String?,
    decimals: Int,
    ceiling: BigInteger,
    chainSymbol: String,
): BigInteger {
    if (inboundDustThreshold == null) {
        throw LimitOrderCancelDustError.ThresholdUnavailable(chainSymbol)
    }
    if (decimals < 0) {
        throw LimitOrderCancelDustError.UnusablePrecision(chainSymbol, decimals)
    }
    val threshold =
        inboundDustThreshold.trim().toBigIntegerOrNull()?.takeIf { it.signum() >= 0 }
            ?: throw LimitOrderCancelDustError.MalformedThreshold(chainSymbol, inboundDustThreshold)
    // Both floors are validated, not just the parsed one: a negative local floor would silently
    // lose
    // to max() and read as "no local requirement".
    if (localDustFloor.signum() < 0) {
        throw LimitOrderCancelDustError.MalformedThreshold(
            chainSymbol,
            "local floor $localDustFloor",
        )
    }

    val thresholdInChainUnits = chainSmallestUnitsFromThorchainBaseUnits(threshold, decimals)
    val amount = localDustFloor.max(thresholdInChainUnits) * DUST_SAFETY_MULTIPLE

    // A zero-value L1 transaction carries no inbound for Bifrost to observe — and neither does one
    // whose value truncates to zero in THORChain's own 1e8 accounting, which is the same
    // invisibility arrived at by arithmetic rather than by a literal zero.
    val observableMinimum = minimumObservableInbound(decimals)
    if (amount < observableMinimum) {
        throw LimitOrderCancelDustError.BelowObservableMinimum(
            chainSymbol,
            amount,
            observableMinimum,
        )
    }
    if (amount > ceiling) {
        throw LimitOrderCancelDustError.ExceedsCeiling(chainSymbol, amount, ceiling)
    }
    return amount
}

/**
 * Re-express an amount THORChain quotes in its own 1e8 fixed point as the source chain's smallest
 * units.
 *
 * Every amount on `inbound_addresses` is 1e8 regardless of the chain: THORNode normalises inbound
 * values through `ConvertAmount` on the way in and publishes its thresholds in that same normalised
 * space.
 *
 * Rounds UP when the chain carries FEWER decimals than THORChain (GAIA's 6). The value is a floor
 * that has to be cleared, and truncation would land it a unit short of exactly the threshold it
 * exists to satisfy.
 */
fun chainSmallestUnitsFromThorchainBaseUnits(value: BigInteger, decimals: Int): BigInteger {
    if (decimals == THORCHAIN_FIXED_POINT_DECIMALS) return value
    if (decimals > THORCHAIN_FIXED_POINT_DECIMALS) {
        return value * BigInteger.TEN.pow(decimals - THORCHAIN_FIXED_POINT_DECIMALS)
    }
    val divisor = BigInteger.TEN.pow(THORCHAIN_FIXED_POINT_DECIMALS - decimals)
    val (quotient, remainder) = value.divideAndRemainder(divisor)
    return if (remainder.signum() == 0) quotient else quotient + BigInteger.ONE
}

/**
 * The smallest amount on a `decimals`-precision chain THORChain can still see, in that chain's
 * smallest units. Anything below is truncated to zero by `ConvertAmount` on the way into
 * THORChain's 1e8 accounting, so Bifrost never raises an inbound for it. On an 18-decimal chain
 * this is 1e10.
 */
fun minimumObservableInbound(decimals: Int): BigInteger =
    if (decimals > THORCHAIN_FIXED_POINT_DECIMALS) {
        BigInteger.TEN.pow(decimals - THORCHAIN_FIXED_POINT_DECIMALS)
    } else {
        BigInteger.ONE
    }

/**
 * The most a cancel on [chain] could plausibly need to attach, in NATURAL units.
 *
 * Deliberately an explicit table rather than a formula. The per-chain minima are known but live in
 * wildly different unit systems (wei vs sats vs uatom), so no single absolute number and no ratio
 * against the local dust floor works across all of them — that floor is defined only for UTXO
 * chains, which would collapse any relative bound.
 *
 * Set roughly an order of magnitude above each chain's live `dust_threshold` doubled — the amount a
 * cancel actually attaches. Loose enough that a legitimate threshold change does not break
 * cancelling, tight enough that a bad value cannot quietly donate a meaningful sum. Sized against
 * the thresholds `inbound_addresses` publishes in natural units: DOGE 1, LTC 0.001, AVAX 0.001,
 * GAIA 0.01, BCH/BSC 0.0001, BTC/ETH 0.00001.
 */
fun limitOrderCancelDustCeiling(chain: Chain): BigDecimal =
    when (chain) {
        // The outlier: a 1 DOGE threshold, so 2 DOGE is the normal attach.
        Chain.Dogecoin -> BigDecimal.TEN
        // Both publish a 0.001 threshold, so 0.002 is the normal attach.
        Chain.Litecoin,
        Chain.Avalanche -> BigDecimal("0.02")
        Chain.Bitcoin,
        Chain.BitcoinCash,
        Chain.Dash,
        Chain.Zcash -> BigDecimal("0.001")
        Chain.GaiaChain,
        Chain.Noble,
        Chain.Kujira -> BigDecimal("0.5")
        // ETH (0.00002 attach), BSC (0.0002) and anything else. Immaterial in fiat on every
        // supported chain while still leaving room for a threshold that moves by an order of
        // magnitude.
        else -> BigDecimal("0.001")
    }

/**
 * The signer's own dust floor for [chain], in its smallest units — zero for every chain that has no
 * such concept. [Chain.getDustThreshold] throws for non-UTXO chains, and a cancel is built for any
 * routable source, so the throw is absorbed here rather than at each call site.
 */
fun limitOrderCancelLocalDustFloor(chain: Chain): BigInteger =
    if (chain.standard == TokenStandard.UTXO) chain.getDustThreshold else BigInteger.ZERO
