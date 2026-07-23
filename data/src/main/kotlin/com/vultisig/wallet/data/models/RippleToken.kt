package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Decimal places the app scales XRPL issued-currency amounts to.
 *
 * Unlike ERC-20 or SPL tokens, an XRPL issued currency carries no on-chain decimals field: amounts
 * travel as decimal strings with 16 significant digits and an exponent between -96 and 80. The
 * wallet's [Coin]/`TokenValue` model is fixed-point, so trust-line balances are pinned to a single
 * scale wide enough to hold the fractional part of any realistic holding while leaving the integer
 * part well inside `BigInteger` range.
 */
const val RIPPLE_TOKEN_DECIMALS: Int = 15

/**
 * Separator between the currency code and issuer address inside a Ripple token's `contractAddress`.
 *
 * Neither half can contain it: XRPL standard currency codes are restricted to letters, digits and
 * `?!@#$%^&*<>(){}[]|`, non-standard ones are 40 hex characters, and issuer addresses are base58.
 */
private const val RIPPLE_TOKEN_SEPARATOR = '.'

/** Currency code reserved for the native asset; it can never name a trust line. */
private const val RIPPLE_NATIVE_CURRENCY = "XRP"

/** Length of the hex form of a 160-bit non-standard currency code. */
private const val RIPPLE_HEX_CURRENCY_LENGTH = 40

/** An XRPL issued currency, identified by its raw on-chain currency code and issuing account. */
data class RippleTokenIdentity(val currency: String, val issuer: String)

/**
 * Wire identity of an issued currency as stored in [Coin.contractAddress] —
 * `"<currency>.<issuer>"`, the notation XRPL tooling uses (e.g.
 * `USD.rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B`). The currency code is kept exactly as the ledger reports
 * it (3-char ASCII or 40-char hex) so it round-trips into `account_lines` comparisons unchanged;
 * [rippleCurrencyTicker] derives the display form.
 */
fun rippleTokenContractAddress(currency: String, issuer: String): String =
    "$currency$RIPPLE_TOKEN_SEPARATOR$issuer"

/** True when this coin is an XRPL issued-currency (trust-line) token rather than native XRP. */
val Coin.isRippleIssuedToken: Boolean
    get() = chain == Chain.Ripple && !isNativeToken && rippleTokenIdentity() != null

/**
 * Splits this coin's `contractAddress` back into its currency/issuer pair, or `null` when the coin
 * is not an XRPL issued currency or its contract address is malformed.
 */
fun Coin.rippleTokenIdentity(): RippleTokenIdentity? {
    if (chain != Chain.Ripple || isNativeToken) return null
    return parseRippleTokenIdentity(contractAddress)
}

fun parseRippleTokenIdentity(contractAddress: String): RippleTokenIdentity? {
    val separator = contractAddress.indexOf(RIPPLE_TOKEN_SEPARATOR)
    if (separator <= 0 || separator == contractAddress.lastIndex) return null
    return RippleTokenIdentity(
        currency = contractAddress.substring(0, separator),
        issuer = contractAddress.substring(separator + 1),
    )
}

/**
 * Human-readable ticker for a raw XRPL currency code.
 *
 * Standard codes are three ASCII characters and are used verbatim. Longer names are carried as the
 * hex of a 160-bit code whose first byte is `0x00`, followed by the zero-padded ASCII name — decode
 * those back to text. Anything else (demurrage or XLS-14 codes, unprintable payloads) has no
 * meaningful text form, so a short hex prefix keeps the row distinguishable.
 */
fun rippleCurrencyTicker(currency: String): String {
    if (currency.length != RIPPLE_HEX_CURRENCY_LENGTH || !currency.all { it.isHexDigit() }) {
        return currency
    }
    val decoded =
        currency
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .dropWhile { it.toInt() == 0 }
            .takeWhile { it.toInt() != 0 }
            .toByteArray()
    val text = String(decoded, Charsets.US_ASCII)
    return if (text.isNotEmpty() && text.all { it.code in 0x20..0x7E }) {
        text
    } else {
        currency.take(RIPPLE_HEX_CURRENCY_PREVIEW_LENGTH).uppercase()
    }
}

/** True when [currency] names the native asset, which is never a valid trust-line currency. */
fun isRippleNativeCurrency(currency: String): Boolean =
    currency.equals(RIPPLE_NATIVE_CURRENCY, ignoreCase = true)

/**
 * Converts an `account_lines` balance string into fixed-point units at [RIPPLE_TOKEN_DECIMALS].
 *
 * A negative balance means the account is the issuing side of the line and owes the counterparty
 * rather than holding anything, so it clamps to zero. Excess precision is truncated down so a
 * displayed balance never rounds up past what the ledger actually holds.
 */
fun String.toRippleTokenUnits(): BigInteger {
    val amount = toBigDecimalOrNull() ?: return BigInteger.ZERO
    if (amount.signum() <= 0) return BigInteger.ZERO
    return amount.setScale(RIPPLE_TOKEN_DECIMALS, RoundingMode.DOWN).unscaledValue()
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val RIPPLE_HEX_CURRENCY_PREVIEW_LENGTH = 8
