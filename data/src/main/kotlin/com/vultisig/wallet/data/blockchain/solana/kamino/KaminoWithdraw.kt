package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoUserPositionJson
import java.math.BigInteger

/**
 * An exact decimal from Kamino's JSON, held as `numerator / 10^scale`.
 *
 * Exists because a rate drives how many shares a withdraw burns and the withdraw endpoint validates
 * nothing: a request naming more shares than the user holds is silently rewritten to `u64::MAX`,
 * which means *withdraw everything*. Any rounding at the far end of a mantissa could therefore turn
 * a partial withdraw into a full exit, so every conversion that sizes a transaction is done in
 * integers and never through floating point.
 */
data class KaminoRate(val numerator: BigInteger, val scale: Int) {

    val isPositive: Boolean
        get() = numerator.signum() > 0

    companion object {
        /**
         * Parses Kamino's plain-decimal form exactly. Rejects grouping separators, exponents and
         * whitespace rather than coercing them — a value that is present and unreadable is a failed
         * read, not a number to guess at.
         */
        fun parse(raw: String?): KaminoRate? {
            if (raw.isNullOrEmpty() || !isPlainDecimal(raw)) return null
            val separator = raw.indexOf('.')
            val digits = if (separator < 0) raw else raw.removeRange(separator, separator + 1)
            val numerator = runCatching { BigInteger(digits) }.getOrNull() ?: return null
            val scale = if (separator < 0) 0 else raw.length - separator - 1
            return KaminoRate(numerator, scale)
        }

        private fun isPlainDecimal(raw: String): Boolean {
            var hasDigit = false
            var hasSeparator = false
            raw.forEachIndexed { index, character ->
                when {
                    character == '-' -> if (index != 0) return false
                    character == '.' -> {
                        if (hasSeparator) return false
                        hasSeparator = true
                    }
                    character in '0'..'9' -> hasDigit = true
                    else -> return false
                }
            }
            return hasDigit
        }
    }
}

/** Widest scale handled. SPL mint decimals are a `u8`; nothing legitimate approaches this. */
private const val MAX_DECIMALS = 18

private fun tenPow(exponent: Int): BigInteger = BigInteger.TEN.pow(exponent)

private fun scalesAreSane(vararg decimals: Int) = decimals.all { it in 0..MAX_DECIMALS }

/** Renders base units as a plain decimal string: no grouping, no exponent, no trailing zeros. */
private fun render(baseUnits: BigInteger, decimals: Int): String {
    val negative = baseUnits.signum() < 0
    val digits = baseUnits.abs().toString()
    if (decimals == 0) return if (negative) "-$digits" else digits

    val padded = digits.padStart(decimals + 1, '0')
    val whole = padded.substring(0, padded.length - decimals)
    val fraction = padded.substring(padded.length - decimals).trimEnd('0')
    val magnitude = if (fraction.isEmpty()) whole else "$whole.$fraction"
    return if (negative) "-$magnitude" else magnitude
}

/**
 * A quantity of vault shares. Distinct from [KaminoTokenAmount] on purpose: the withdraw endpoint
 * takes shares while the deposit endpoint takes the underlying token, and confusing the two is off
 * by the share rate — about 930× on the SOL vault, which is far enough over a real balance to
 * trigger the `u64::MAX` full-exit rewrite.
 */
data class KaminoShareAmount(val baseUnits: BigInteger, val decimals: Int) {

    val isZero: Boolean
        get() = baseUnits.signum() == 0

    /** The decimal string the API expects. */
    fun apiString(): String = render(baseUnits, decimals)

    /** What these shares are worth in the underlying token, truncated. */
    fun tokenValue(rate: KaminoRate, tokenDecimals: Int): KaminoTokenAmount? =
        convert(rate, tokenDecimals, roundUp = false)

    /**
     * Rounded up, so an amount at or above the result converts back to at least these shares. Used
     * for the vault minimum, which must not be understated into a request the vault rejects.
     */
    fun tokenValueRoundedUp(rate: KaminoRate, tokenDecimals: Int): KaminoTokenAmount? =
        convert(rate, tokenDecimals, roundUp = true)

    private fun convert(
        rate: KaminoRate,
        tokenDecimals: Int,
        roundUp: Boolean,
    ): KaminoTokenAmount? {
        if (!rate.isPositive || baseUnits.signum() < 0) return null
        if (!scalesAreSane(tokenDecimals, decimals) || rate.scale !in 0..(MAX_DECIMALS * 4)) {
            return null
        }
        // tokensBase = sharesBase × numerator × 10^tokenDecimals ÷ 10^(shareDecimals + rateScale)
        val numerator = baseUnits.multiply(rate.numerator).multiply(tenPow(tokenDecimals))
        val denominator = tenPow(decimals + rate.scale)
        val quotient =
            if (roundUp) {
                numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator)
            } else {
                numerator.divide(denominator)
            }
        return KaminoTokenAmount(quotient, tokenDecimals)
    }

    companion object {
        fun parse(decimalString: String?, decimals: Int): KaminoShareAmount? {
            val rate = KaminoRate.parse(decimalString) ?: return null
            if (!scalesAreSane(decimals) || rate.scale > MAX_DECIMALS * 4) return null
            // Re-scale numerator/10^rateScale to base units at `decimals`.
            return if (rate.scale <= decimals) {
                KaminoShareAmount(rate.numerator.multiply(tenPow(decimals - rate.scale)), decimals)
            } else {
                KaminoShareAmount(rate.numerator.divide(tenPow(rate.scale - decimals)), decimals)
            }
        }

        fun zero(decimals: Int) = KaminoShareAmount(BigInteger.ZERO, decimals)
    }
}

/** A quantity of a vault's underlying token — what the user thinks in and what the form shows. */
data class KaminoTokenAmount(val baseUnits: BigInteger, val decimals: Int) {

    fun apiString(): String = render(baseUnits, decimals)

    /**
     * The shares this token amount burns, truncated.
     *
     * Rounding down is a safety property, not a preference: an over-request by a single base unit
     * is rewritten by the API to a full exit.
     */
    fun shareAmount(rate: KaminoRate, shareDecimals: Int): KaminoShareAmount? =
        shareAmount(rate, shareDecimals, roundUp = false)

    /**
     * The same conversion rounded up. Used for one thing only: turning a token-denominated minimum
     * into the share count a withdraw has to name to clear it — rounding down there would produce a
     * share figure worth fractionally less than the minimum.
     *
     * Never use this to size an actual withdraw. Rounding up is exactly the direction that turns a
     * partial withdraw into an over-request, which the API rewrites to a full exit.
     */
    fun shareAmountRoundedUp(rate: KaminoRate, shareDecimals: Int): KaminoShareAmount? =
        shareAmount(rate, shareDecimals, roundUp = true)

    private fun shareAmount(
        rate: KaminoRate,
        shareDecimals: Int,
        roundUp: Boolean,
    ): KaminoShareAmount? {
        if (!rate.isPositive || baseUnits.signum() < 0) return null
        if (!scalesAreSane(shareDecimals, decimals) || rate.scale !in 0..(MAX_DECIMALS * 4)) {
            return null
        }
        // sharesBase = tokensBase × 10^(rateScale + shareDecimals) ÷ (10^tokenDecimals × numerator)
        val numerator = baseUnits.multiply(tenPow(rate.scale + shareDecimals))
        val denominator = tenPow(decimals).multiply(rate.numerator)
        val quotient =
            if (roundUp) {
                numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator)
            } else {
                numerator.divide(denominator)
            }
        return KaminoShareAmount(quotient, shareDecimals)
    }

    companion object {
        fun parse(decimalString: String?, decimals: Int): KaminoTokenAmount? =
            KaminoShareAmount.parse(decimalString, decimals)?.let {
                KaminoTokenAmount(it.baseUnits, decimals)
            }

        fun zero(decimals: Int) = KaminoTokenAmount(BigInteger.ZERO, decimals)
    }
}

/** A rate scaled to a mint's precision, remembering whether anything was thrown away. */
private data class ScaledAmount(val baseUnits: BigInteger, val isExact: Boolean)

/**
 * Scales [rate] to [decimals] base units, reporting whether the conversion was lossless.
 *
 * Exactness is what decides the step back from the API's sentinel: when digits had to be discarded
 * the truncated value is already strictly below the balance, and when it did not the value *is* the
 * balance and has to give up a unit.
 */
private fun scaleReportingExactness(rate: KaminoRate, decimals: Int): ScaledAmount? {
    if (!scalesAreSane(decimals) || rate.scale > MAX_DECIMALS * 4) return null
    return if (rate.scale <= decimals) {
        ScaledAmount(rate.numerator.multiply(tenPow(decimals - rate.scale)), isExact = true)
    } else {
        val divisor = tenPow(rate.scale - decimals)
        val (quotient, remainder) = rate.numerator.divideAndRemainder(divisor)
        ScaledAmount(quotient, isExact = remainder.signum() == 0)
    }
}

/** The share balance a withdraw may spend, and how the vault currently holds it. */
data class KaminoWithdrawableShares(
    /**
     * The most a withdraw may name — the position stepped back from the API's `u64::MAX` sentinel.
     */
    val maximum: KaminoShareAmount,
    /**
     * The part already sitting in the user's own share account, which a withdraw spends without
     * touching the farm. The remainder is what the farm has to release first.
     */
    val unstaked: KaminoShareAmount,
)

/** The user's share balance in one vault, split the way `/positions` reports it. */
data class KaminoSharePosition(
    val staked: KaminoShareAmount,
    val unstaked: KaminoShareAmount,
    val total: KaminoShareAmount,
    private val spendableShares: KaminoShareAmount,
    private val partsSumToTotal: Boolean,
) {
    /**
     * Whether the three reported numbers can all be true at once. A response claiming more of
     * either kind than the total is one whose figures cannot be spent as a balance — and spending a
     * balance that is too large is exactly what the API turns into `u64::MAX`.
     */
    val isPlausible: Boolean
        get() =
            staked.baseUnits.signum() >= 0 &&
                unstaked.baseUnits.signum() >= 0 &&
                staked.baseUnits <= total.baseUnits &&
                unstaked.baseUnits <= total.baseUnits

    /**
     * The most a withdraw may name: the total, stepped back from the API's sentinel.
     *
     * A request naming more shares than are held is rewritten to `u64::MAX` — withdraw everything —
     * so the balance itself is indistinguishable, in the bytes, from an amount nobody asked for.
     * The maximum therefore has to be the largest amount strictly beneath it. Truncating the
     * reported string is necessary but not sufficient: it lands strictly below only when there were
     * digits past the mint's scale to discard, so the exact case gives up one base unit — 10^-6 of
     * a share, about a ten-thousandth of a cent on these vaults — and the inexact case does not
     * have to.
     */
    val spendable: KaminoShareAmount
        get() = spendableShares

    /**
     * Whether the two reported parts add up to the reported total.
     *
     * Compared at the API's **own** precision, never at the share mint's scale. Truncating three
     * strings to six decimals and adding two of them can miss the third by a base unit with nothing
     * wrong — `0.9445485 + 0.9595935` truncates to `944548 + 959593`, one short of `1904142` — and
     * refusing a real position over a last decimal place is not a guard, it is a bug.
     */
    val accountsForItsTotal: Boolean
        get() = partsSumToTotal

    companion object {
        fun parse(response: KaminoUserPositionJson, shareDecimals: Int): KaminoSharePosition? {
            val stakedRate = KaminoRate.parse(response.stakedShares) ?: return null
            val unstakedRate = KaminoRate.parse(response.unstakedShares) ?: return null
            val totalRate = KaminoRate.parse(response.totalShares) ?: return null

            val staked =
                KaminoShareAmount.parse(response.stakedShares, shareDecimals) ?: return null
            val unstaked =
                KaminoShareAmount.parse(response.unstakedShares, shareDecimals) ?: return null
            val total = KaminoShareAmount.parse(response.totalShares, shareDecimals) ?: return null

            val scaledTotal = scaleReportingExactness(totalRate, shareDecimals) ?: return null
            val spendableUnits =
                if (scaledTotal.isExact) scaledTotal.baseUnits.subtract(BigInteger.ONE)
                else scaledTotal.baseUnits

            return KaminoSharePosition(
                staked = staked,
                unstaked = unstaked,
                total = total,
                spendableShares =
                    KaminoShareAmount(spendableUnits.max(BigInteger.ZERO), shareDecimals),
                partsSumToTotal = sumsExactly(stakedRate, unstakedRate, totalRate),
            )
        }

        /** Adds the two parts and compares them to the total at whatever precision the API used. */
        private fun sumsExactly(
            staked: KaminoRate,
            unstaked: KaminoRate,
            total: KaminoRate,
        ): Boolean {
            val scale = maxOf(staked.scale, unstaked.scale, total.scale)
            if (scale > MAX_DECIMALS * 4) return false
            fun at(rate: KaminoRate) = rate.numerator.multiply(tenPow(scale - rate.scale))
            return at(staked).add(at(unstaked)) == at(total)
        }
    }
}

/**
 * What a withdraw from one vault may do right now.
 *
 * Every deposit into the launch vaults auto-stakes its shares into the vault's farm, so a staked
 * position is not an edge case — it is what essentially every real holder is in. The transaction
 * that spends those shares releases them from the farm first (`farms::unstake`, then
 * `farms::withdraw_unstaked_deposits`, both ahead of the vault withdraw), which Kamino builds, so a
 * staked balance is withdrawable rather than refused.
 *
 * The refusals that remain are about the READ, not the shape: a response whose figures contradict
 * each other, and one whose parts do not add up to its total. Neither describes a position a
 * request can be sized against.
 */
sealed interface KaminoWithdrawEligibility {

    /** The user holds shares that can be withdrawn, staked or not. */
    data class Withdrawable(val shares: KaminoWithdrawableShares) : KaminoWithdrawEligibility

    /** The user holds no shares in this vault. */
    data object Empty : KaminoWithdrawEligibility

    /**
     * The position could not be read. Refused rather than treated as zero (a silent "nothing to
     * withdraw" on a real position) or as whole (an amount the user does not have).
     */
    data object Unreadable : KaminoWithdrawEligibility

    val withdrawableShares: KaminoWithdrawableShares?
        get() = (this as? Withdrawable)?.shares

    companion object {
        /**
         * @param response this vault's entry, or null when the vault is absent — a real "holds
         *   nothing".
         */
        fun resolve(
            response: KaminoUserPositionJson?,
            shareDecimals: Int,
        ): KaminoWithdrawEligibility {
            if (response == null) return Empty
            val position =
                KaminoSharePosition.parse(response, shareDecimals)?.takeIf { it.isPlausible }
                    ?: return Unreadable

            // A total larger than its parts is a position this app cannot describe: it would have
            // to
            // guess how much of the remainder is staked, and that guess is the argument of an
            // instruction it then claims to have verified.
            if (!position.accountsForItsTotal) return Unreadable
            // Nothing left once the maximum has stepped back from the sentinel. A position of a
            // single base unit lands here, and "nothing to withdraw" is the true statement about
            // it.
            if (position.spendable.isZero) return Empty

            return Withdrawable(
                KaminoWithdrawableShares(maximum = position.spendable, unstaked = position.unstaked)
            )
        }
    }
}

/**
 * Whether the vault can settle a withdraw out of the balance it holds liquid.
 *
 * Not an error state. The liquid buffer measures 0.36% of the Steakhouse vault and 0.04% of the
 * Allez vault, so a withdraw exceeding it is the common case rather than an edge one. What happens
 * on chain when the buffer is short has not been observed, so nothing here claims to know: this is
 * a comparison of two published numbers, not a decode of a program error.
 */
sealed interface KaminoWithdrawLiquidity {
    data object Instant : KaminoWithdrawLiquidity

    data class Delayed(val available: KaminoTokenAmount) : KaminoWithdrawLiquidity

    companion object {
        fun resolve(
            requested: KaminoTokenAmount,
            available: KaminoTokenAmount?,
        ): KaminoWithdrawLiquidity =
            if (available != null && requested.baseUnits > available.baseUnits) {
                Delayed(available)
            } else {
                Instant
            }
    }
}

/**
 * The conversions a withdraw form performs, in one place so its gate, its maximum and the amount it
 * actually sends can never disagree.
 */
object KaminoWithdrawMath {

    /**
     * The most the held shares are worth in the underlying token, truncated. The form's maximum.
     */
    fun maximumTokens(
        shares: KaminoShareAmount,
        rate: KaminoRate,
        tokenDecimals: Int,
    ): KaminoTokenAmount? = shares.tokenValue(rate, tokenDecimals)

    /**
     * The vault's minimum withdraw, in SHARES, padded for the kVault program's real floor.
     *
     * `minWithdrawAmount` is TOKEN base units — the program compares it against the token value
     * being withdrawn, `available_to_send_to_user + invested_liquidity_to_send_to_user`, not
     * against a share count. Measured on chain the real floor sits above the published figure,
     * roughly double it, and that gap is a live value rather than a fixed boundary, so this matches
     * iOS's tested margin (3x the published figure) instead of hard-coding the measured one.
     *
     * Rounded up on the way back to shares so a share count worth fractionally less than the
     * required token amount is never offered as if it cleared the minimum.
     */
    fun effectiveMinimumShares(
        published: KaminoTokenAmount,
        rate: KaminoRate,
        shareDecimals: Int,
    ): KaminoShareAmount? {
        val required =
            KaminoTokenAmount(
                published.baseUnits.multiply(MINIMUM_WITHDRAW_MULTIPLE),
                published.decimals,
            )
        return required.shareAmountRoundedUp(rate, shareDecimals)
    }

    /** A margin of this many times the published minimum, matching iOS's tested multiple. */
    private val MINIMUM_WITHDRAW_MULTIPLE = BigInteger.valueOf(3)

    /**
     * The vault's share-denominated minimum expressed in the token, rounded up so anything at or
     * above it converts back to at least the minimum.
     */
    fun minimumTokens(
        minimumShares: KaminoShareAmount,
        rate: KaminoRate,
        tokenDecimals: Int,
    ): KaminoTokenAmount? = minimumShares.tokenValueRoundedUp(rate, tokenDecimals)

    /**
     * The shares a withdraw of [tokens] burns, or null when no withdraw of that size can be sized
     * safely.
     *
     * **This is the fund-safety boundary of the whole flow.** The API validates nothing: a withdraw
     * naming more shares than the user holds is silently rewritten to `u64::MAX`, which means
     * *withdraw everything*. An over-request by one base unit turns a partial withdraw into a full
     * exit. Three branches, each a different answer to that:
     * - **Above the maximum**: refused. Nothing may read "more than the position" as "the position"
     *   — a mistyped digit would then be a full exit nobody asked for. Refusing is also the only
     *   answer that stays true if the balance moved since it was read.
     * - **Exactly the maximum**: a full withdraw, sending [held] — the exact balance that was read
     *   — rather than a number converted out of a token amount. Round-tripping the maximum through
     *   tokens and back is precisely how the extra base unit gets introduced.
     * - **Below it**: the conversion truncates. [maximumTokens] and this division both round down,
     *   so `tokens < maximumTokens` implies the result is `<= held`.
     */
    fun sharesForTokens(
        tokens: KaminoTokenAmount,
        held: KaminoShareAmount,
        maximumTokens: KaminoTokenAmount,
        rate: KaminoRate,
        shareDecimals: Int,
    ): KaminoShareAmount? {
        if (tokens.baseUnits.signum() <= 0 || held.baseUnits.signum() <= 0) return null
        if (tokens.baseUnits > maximumTokens.baseUnits) return null
        if (tokens.baseUnits == maximumTokens.baseUnits) return held
        // A token amount smaller than one share is worth zero shares once truncated, and a request
        // for zero is not a withdraw — it would build a transaction that moves nothing while
        // telling
        // the user it moves something. The vault minimum normally catches this, but it is only
        // advisory when the vault state could not be read.
        return tokens.shareAmount(rate, shareDecimals)?.takeIf { !it.isZero }
    }
}
