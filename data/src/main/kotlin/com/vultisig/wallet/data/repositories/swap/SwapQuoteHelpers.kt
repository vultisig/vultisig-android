package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.utils.thorswapMultiplier
import java.math.BigDecimal
import java.math.BigInteger

/**
 * THORChain/Maya pool quotes are denominated in 1e8 units regardless of the source token decimals.
 * This converts the caller's [tokenValue] into the protocol's native unit.
 */
internal fun Coin.toThorTokenValue(tokenValue: TokenValue): BigInteger =
    (tokenValue.decimal * thorswapMultiplier).toBigInteger()

/** Inverse of [toThorTokenValue]: scales an amount returned by THOR/Maya back to [token] units. */
internal fun String.convertToTokenValue(token: Coin): TokenValue {
    val scaled = BigDecimal(this).divide(token.thorswapMultiplier)
    return TokenValue(value = scaled.movePointRight(token.decimal).toBigInteger(), token = token)
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
