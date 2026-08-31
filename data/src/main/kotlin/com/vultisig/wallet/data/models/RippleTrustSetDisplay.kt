package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat

/**
 * Terms of an XRPL TrustSet as the signer will encode them. Read from the coin's token id and the
 * payload amount, never the relayed `toAddress` or `coin.ticker`, which can name a different issuer
 * and currency than the one being signed.
 */
data class RippleTrustSetDisplay(val ticker: String, val issuer: String, val limitValue: String)

/** Null for the token ids and currency codes the signer itself refuses. */
fun rippleTrustSetDisplay(token: Coin, amount: BigInteger): RippleTrustSetDisplay? {
    val identity = token.rippleTokenIdentity() ?: return null
    val code =
        toRippleCurrencyCodeOrNull(identity.currency)?.takeIf(::isSignableRippleCurrencyCode)
            ?: return null
    return RippleTrustSetDisplay(
        ticker = rippleCurrencyTicker(code),
        issuer = identity.issuer,
        limitValue = amount.toRippleTokenValue(token.decimal),
    )
}

val RippleTrustSetDisplay.groupedLimit: String
    get() = NumberFormat.getInstance().format(BigDecimal(limitValue))
