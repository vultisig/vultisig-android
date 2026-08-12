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
    fun shareAmount(rate: KaminoRate, shareDecimals: Int): KaminoShareAmount? {
        if (!rate.isPositive || baseUnits.signum() < 0) return null
        if (!scalesAreSane(shareDecimals, decimals) || rate.scale !in 0..(MAX_DECIMALS * 4)) {
            return null
        }
        // sharesBase = tokensBase × 10^(rateScale + shareDecimals) ÷ (10^tokenDecimals × numerator)
        val numerator = baseUnits.multiply(tenPow(rate.scale + shareDecimals))
        val denominator = tenPow(decimals).multiply(rate.numerator)
        return KaminoShareAmount(numerator.divide(denominator), shareDecimals)
    }

    companion object {
        fun parse(decimalString: String?, decimals: Int): KaminoTokenAmount? =
            KaminoShareAmount.parse(decimalString, decimals)?.let {
                KaminoTokenAmount(it.baseUnits, decimals)
            }

        fun zero(decimals: Int) = KaminoTokenAmount(BigInteger.ZERO, decimals)
    }
}

/** The user's share balance in one vault, split the way `/positions` reports it. */
data class KaminoSharePosition(
    val staked: KaminoShareAmount,
    val unstaked: KaminoShareAmount,
    val total: KaminoShareAmount,
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
     * Whether the position is unstaked shares and nothing else.
     *
     * Stated as `unstaked == total` rather than `staked + unstaked == total`: both hold for a
     * wholly unstaked position, but the sum can disagree by a base unit purely from truncating two
     * different decimal strings, which would refuse a real position over its last decimal place.
     */
    val holdsOnlyUnstakedShares: Boolean
        get() = unstaked.baseUnits == total.baseUnits

    companion object {
        fun parse(response: KaminoUserPositionJson, shareDecimals: Int): KaminoSharePosition? {
            val staked =
                KaminoShareAmount.parse(response.stakedShares, shareDecimals) ?: return null
            val unstaked =
                KaminoShareAmount.parse(response.unstakedShares, shareDecimals) ?: return null
            val total = KaminoShareAmount.parse(response.totalShares, shareDecimals) ?: return null
            return KaminoSharePosition(staked, unstaked, total)
        }
    }
}

/**
 * What a withdraw from one vault may do right now.
 *
 * [FarmStaked] is the reason this exists. Every deposit into the launch vaults auto-stakes its
 * shares into the vault's farm, and the transaction that spends *staked* shares has never been
 * observed — so the validator has no template for it and building one would mean asserting against
 * a guessed instruction layout. This makes that a named, user-visible state instead of a refusal
 * deep inside the pipeline.
 */
sealed interface KaminoWithdrawEligibility {

    /** The observed path. A full withdraw sends precisely [shares], never a converted number. */
    data class Withdrawable(val shares: KaminoShareAmount) : KaminoWithdrawEligibility

    /** Shares are staked in the vault's farm. Nothing is built. */
    data class FarmStaked(val shares: KaminoShareAmount) : KaminoWithdrawEligibility

    /** The user holds no shares in this vault. */
    data object Empty : KaminoWithdrawEligibility

    /**
     * The position could not be read. Refused rather than treated as zero (a silent "nothing to
     * withdraw" on a real position) or as whole (an amount the user does not have).
     */
    data object Unreadable : KaminoWithdrawEligibility

    val withdrawableShares: KaminoShareAmount?
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

            // Staked first and unconditionally: a position even partly staked cannot be fully
            // withdrawn by the transaction shape this app knows, and offering to spend only the
            // unstaked remainder would quietly under-withdraw.
            if (!position.staked.isZero) return FarmStaked(position.staked)
            // `staked == 0` means nothing was *reported* as staked; a total larger than the parts
            // is
            // a position this app cannot describe, and an unaccounted share may well be staked.
            if (!position.holdsOnlyUnstakedShares) return Unreadable
            if (position.unstaked.isZero) return Empty
            return Withdrawable(position.unstaked)
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
        return tokens.shareAmount(rate, shareDecimals)
    }
}
