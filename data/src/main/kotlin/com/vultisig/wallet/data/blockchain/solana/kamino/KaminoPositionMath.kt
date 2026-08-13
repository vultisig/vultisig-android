package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts Kamino's wire values into the quantities the Earn rows display.
 *
 * Kept pure and currency-free on purpose. Kamino prices everything in USD, but the app renders in
 * whichever currency the user picked, so fiat conversion stays with the app's own price pipeline
 * and only token quantities are derived here.
 */
object KaminoPositionMath {

    /**
     * Parses one of Kamino's decimal strings, or null when it is absent or unparseable.
     *
     * A malformed number is treated as missing rather than as zero: a row that shows nothing is
     * recoverable, whereas a balance silently rendered as 0 reads as "your deposit is gone".
     */
    fun decimalOrNull(raw: String?): BigDecimal? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    /**
     * Token amount a share balance is worth.
     *
     * `shares` and `tokensPerShare` both arrive as decimal quantities, so this is a plain product —
     * no scaling by either mint's decimals, which for these vaults are not even equal to each
     * other.
     *
     * Deliberately not Kamino's documented `(totalShares / sharesIssued) × tokenAvailable`:
     * `tokenAvailable` is only the vault's liquid buffer, a fraction of a percent of assets under
     * management, so that formula understates a position by orders of magnitude.
     *
     * Rounded down to the token's own precision so a displayed balance is never larger than what
     * can actually be withdrawn.
     */
    fun tokenAmount(
        shares: BigDecimal,
        tokensPerShare: BigDecimal,
        tokenDecimals: Int,
    ): BigDecimal = shares.multiply(tokensPerShare).setScale(tokenDecimals, RoundingMode.DOWN)

    /**
     * Turns Kamino's APY fraction into a percentage. `0.039967…` becomes `4.00`.
     *
     * Every `apy*` field is a fraction; rendering one straight into a `%` slot would report a 4%
     * vault as 0.04%.
     */
    fun apyPercent(apyFraction: BigDecimal): BigDecimal =
        apyFraction.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP)

    /**
     * Converts a minimum expressed in the token's base units into a decimal amount — 100000 at 6
     * decimals is 0.1. This is the one place Kamino uses base units rather than decimal strings.
     */
    fun baseUnitsToDecimal(baseUnits: String?, tokenDecimals: Int): BigDecimal? =
        decimalOrNull(baseUnits)?.movePointLeft(tokenDecimals)

    private val ONE_HUNDRED = BigDecimal(100)
}
