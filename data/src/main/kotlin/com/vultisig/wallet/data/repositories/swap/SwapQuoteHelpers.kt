package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.utils.thorswapMultiplier
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal val BPS_DIVISOR: BigInteger = BigInteger.valueOf(10_000)

private const val THORSWAP_DECIMAL_SCALE = 8

/**
 * THORChain/Maya pool quotes are denominated in 1e8 units regardless of the source token decimals.
 * This converts the caller's [tokenValue] into the protocol's native unit.
 */
internal fun Coin.toThorTokenValue(tokenValue: TokenValue): BigInteger =
    (tokenValue.decimal * thorswapMultiplier).toBigInteger()

/** Inverse of [toThorTokenValue]: scales [amount] returned by THOR/Maya into this coin's units. */
internal fun Coin.convertToTokenValue(amount: String): TokenValue {
    val scaled =
        BigDecimal(amount).divide(thorswapMultiplier, THORSWAP_DECIMAL_SCALE, RoundingMode.DOWN)
    return TokenValue(value = scaled.movePointRight(decimal).toBigInteger(), token = this)
}

internal fun THORChainSwapQuoteDeserialized.unwrapOrThrow(): THORChainSwapQuote =
    when (this) {
        is THORChainSwapQuoteDeserialized.Error ->
            throw SwapException.handleSwapException(error.message)

        is THORChainSwapQuoteDeserialized.Result -> {
            data.error?.let { throw SwapException.handleSwapException(it) }
            data
        }
    }

/**
 * Wraps an API call so any non-cancellation exception is rethrown as a [SwapException]. Logs the
 * original cause via Timber so the stack trace is preserved for debugging. Re-thrown
 * [SwapException]s are passed through unchanged so their typed subtype is preserved.
 */
internal suspend inline fun <T> swapApiCall(tag: String, crossinline block: suspend () -> T): T =
    try {
        block()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: SwapException) {
        throw e
    } catch (e: Exception) {
        timber.log.Timber.w(e, "%s quote fetch failed", tag)
        throw SwapException.handleSwapException(e.message ?: "Unknown error")
    }
