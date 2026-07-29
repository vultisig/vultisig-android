package com.vultisig.wallet.data.swap.limit

import java.math.BigDecimal
import java.math.BigInteger

/**
 * THORChain expresses quote amounts and memo LIM values in 1e8 fixed point, regardless of the
 * asset's own decimals.
 */
const val THORCHAIN_FIXED_POINT_DECIMALS = 8

private val THORCHAIN_SCALE: BigInteger = BigInteger.TEN.pow(THORCHAIN_FIXED_POINT_DECIMALS)

/**
 * Convert an amount from a coin's native smallest units (`10^decimals`) to THORChain's 1e8 fixed
 * point.
 *
 * Both the quote endpoint's `amount` and the limit-swap memo's `source_amount` are 1e8-scaled. An
 * 8-decimal source (BTC/RUNE) already matches and this is a no-op, but an 18-decimal source (ETH)
 * passed through raw would be 1e10x too large — producing a mis-scaled quote and, worse, a LIM that
 * encodes a completely different minimum-received into the signed memo. Multiplies before dividing
 * so precision is not lost on the way down; floors sub-1e8 dust rather than rounding up.
 */
fun toThorchainFixedPoint(amount: BigInteger, decimals: Int): BigInteger =
    amount * THORCHAIN_SCALE / BigInteger.TEN.pow(decimals)

/**
 * Interpret a THORChain 1e8 fixed-point amount as a natural-unit number.
 *
 * Used for display and price math only — never to derive a value that ends up in a signed memo,
 * where the [BigInteger] path above is authoritative.
 */
fun fromThorchainFixedPoint(amount: BigInteger): BigDecimal =
    BigDecimal(amount).divide(BigDecimal(THORCHAIN_SCALE))
