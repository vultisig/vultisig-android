package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.common.toHex
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
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

/** Ceiling on a wire-supplied token scale; no real coin comes close to it. */
private const val MAX_RIPPLE_TOKEN_SCALE = 100

/** Significant digits an XRPL issued-currency amount carries; beyond it the signer hard-errors. */
private const val RIPPLE_VALUE_PRECISION = 16

/**
 * Separator between the currency code and issuer address inside a Ripple token's `contractAddress`.
 *
 * Neither half can contain it: XRPL standard currency codes are restricted to letters, digits and
 * `?!@#$%^&*<>(){}[]|`, non-standard ones are 40 hex characters, and issuer addresses are base58.
 */
private const val RIPPLE_TOKEN_SEPARATOR = '.'

/** Currency code reserved for the native asset; it can never name a trust line. */
private const val RIPPLE_NATIVE_CURRENCY = "XRP"

/** Length of an XRPL "standard" currency code, the only form written as readable characters. */
private const val RIPPLE_STANDARD_CURRENCY_LENGTH = 3

/** An XRPL currency code is 160 bits: 20 bytes, or 40 characters once hex-encoded. */
private const val RIPPLE_CURRENCY_CODE_BYTES = 20
private val RIPPLE_HEX_CURRENCY_CODE = Regex("[0-9a-fA-F]{${RIPPLE_CURRENCY_CODE_BYTES * 2}}")

/**
 * The repertoire a standard code may draw on, which is rippled's own `kIsoCharSet`
 * (`src/libxrpl/protocol/UintTypes.cpp`) minus the lowercase letters — see
 * [isSignableRippleCurrencyCode] for why those are excluded.
 */
private const val RIPPLE_SIGNABLE_CURRENCY_CHARS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<>(){}[]|?!@#$%^&*"

/** An XRPL issued currency, identified by its raw on-chain currency code and issuing account. */
data class RippleTokenIdentity(val currency: String, val issuer: String)

/**
 * Wire identity of an issued currency as stored in [Coin.contractAddress] —
 * `"<currency>.<issuer>"`, the notation XRPL tooling uses (e.g.
 * `USD.rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B`). The currency code is kept exactly as the ledger reports
 * it (3-char ASCII or 40-char hex) so it round-trips into `account_lines` comparisons unchanged.
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
    // Neither half may contain the separator, so a second one means the address was not built by
    // rippleTokenContractAddress; splitting on the first would hand back an issuer that can never
    // match a trust line.
    if (contractAddress.lastIndexOf(RIPPLE_TOKEN_SEPARATOR) != separator) return null
    return RippleTokenIdentity(
        currency = contractAddress.substring(0, separator),
        issuer = contractAddress.substring(separator + 1),
    )
}

/** True when [currency] names the native asset, which is never a valid trust-line currency. */
fun isRippleNativeCurrency(currency: String): Boolean =
    currency.equals(RIPPLE_NATIVE_CURRENCY, ignoreCase = true)

/**
 * Converts an `account_lines` balance string into fixed-point units at [decimals], which defaults
 * to [RIPPLE_TOKEN_DECIMALS] but should be the owning coin's own scale so the parsed value matches
 * how the balance is later rendered.
 *
 * A negative balance means the account is the issuing side of the line and owes the counterparty
 * rather than holding anything, so it clamps to zero. Excess precision is truncated down so a
 * displayed balance never rounds up past what the ledger actually holds.
 */
fun String.toRippleTokenUnits(decimals: Int = RIPPLE_TOKEN_DECIMALS): BigInteger {
    val amount = toBigDecimalOrNull() ?: return BigInteger.ZERO
    if (amount.signum() <= 0) return BigInteger.ZERO
    return amount.setScale(decimals, RoundingMode.DOWN).unscaledValue()
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

/**
 * Renders fixed-point [decimals]-scaled units as the decimal string an XRPL amount carries. Inverse
 * of [toRippleTokenUnits].
 */
fun BigInteger.toRippleTokenValue(decimals: Int): String =
    toRippleDecimal(decimals).stripTrailingZeros().toPlainString()

/**
 * Truncates [decimals]-scaled units down to what an XRPL issued-currency amount can express.
 *
 * [RIPPLE_TOKEN_DECIMALS] is the right scale to read a ledger balance at, but one digit wider than
 * the ledger once an amount also has an integer part: a fraction of a full-precision balance, or an
 * amount derived from a fiat price, lands on 17 or 18 significant digits and the signer refuses it
 * outright. Truncation never yields more than was asked for.
 */
fun BigInteger.toRepresentableRippleTokenUnits(decimals: Int): BigInteger {
    val value = toRippleDecimal(decimals)
    if (value.stripTrailingZeros().precision() <= RIPPLE_VALUE_PRECISION) return this

    return value
        .round(MathContext(RIPPLE_VALUE_PRECISION, RoundingMode.DOWN))
        .movePointRight(decimals)
        .toBigInteger()
}

/**
 * [decimals] is the owning coin's own scale, which can arrive from a peer over the wire, so it is
 * bounded here: a negative scale would multiply instead of divide, and an astronomical one would
 * expand into gigabytes of digits.
 */
private fun BigInteger.toRippleDecimal(decimals: Int): BigDecimal {
    require(decimals in 0..MAX_RIPPLE_TOKEN_SCALE) { "Unsupported Ripple token scale $decimals" }
    return BigDecimal(this, decimals)
}

/**
 * The on-ledger form of a currency code or human ticker.
 *
 * A 3-character standard code is kept exactly as written — XRPL currency codes are case-sensitive,
 * so `usd` and `USD` are different currencies. An already-encoded 40-character hex code is
 * upper-cased. Everything else is packed into the 160-bit form: its ASCII bytes right-padded with
 * zeros to 20 bytes, hex-encoded. A ticker longer than three characters, `RLUSD` included, is only
 * expressible that way, which is why the catalog stores it as hex.
 */
fun toRippleCurrencyCode(currency: String): String {
    val value = currency.trim()
    if (value.length == RIPPLE_STANDARD_CURRENCY_LENGTH) return value
    if (value.isRippleHexCurrencyCode()) return value.uppercase()

    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= RIPPLE_CURRENCY_CODE_BYTES) {
        "Ripple currency code '$value' exceeds $RIPPLE_CURRENCY_CODE_BYTES bytes"
    }
    return bytes.copyOf(RIPPLE_CURRENCY_CODE_BYTES).toHex().uppercase()
}

/**
 * True when a code already in on-ledger form ([toRippleCurrencyCode]) reaches the ledger unchanged.
 *
 * WalletCore encodes a 3-byte code by upper-casing it first (`Currency::from_str`), while XRPL
 * copies those bytes verbatim and treats them case-sensitively. A lowercase standard code would
 * therefore be signed as a different currency than the one reviewed — a trust line opened on the
 * wrong asset, or a payment the ledger rejects after the ceremony. Nothing is lost by refusing it:
 * the same currency stays expressible through its 40-character hex spelling.
 */
fun isSignableRippleCurrencyCode(code: String): Boolean =
    if (code.isRippleHexCurrencyCode()) {
        code == code.uppercase()
    } else {
        code.length == RIPPLE_STANDARD_CURRENCY_LENGTH &&
            code.all { it in RIPPLE_SIGNABLE_CURRENCY_CHARS }
    }

private fun String.isRippleHexCurrencyCode(): Boolean = RIPPLE_HEX_CURRENCY_CODE.matches(this)
