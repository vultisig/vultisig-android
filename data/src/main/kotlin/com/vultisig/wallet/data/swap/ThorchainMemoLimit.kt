package com.vultisig.wallet.data.swap

import java.math.BigInteger

/**
 * Reads the minimum-output floor (`LIM`) a THORChain / MayaChain swap memo asserts.
 *
 * The node bakes `LIM = expected_amount_out × (10_000 − tolerance_bps) / 10_000` into the memo it
 * returns, and that memo is signed verbatim (`SwapTransactionBuilder` passes `quote.data.memo`
 * straight through, and `UtxoHelper` writes it into the OP_RETURN unchanged). So the floor is read
 * back out of the memo rather than re-derived from the tolerance we sent: the node owns the
 * rounding and the streaming split, and a client-side re-derivation would drift from it.
 *
 * With "Auto" slippage the app sends no `tolerance_bps` at all
 * ([com.vultisig.wallet.data.repositories.swap.DEFAULT_THORCHAIN_TOLERANCE_BPS]), so the memo
 * carries no limit and this returns null — the swap accepts any output. Aggregator routes (1inch /
 * KyberSwap / LI.FI / Jupiter / SwapKit) sign opaque calldata instead of a memo, so whatever floor
 * their own router enforces is not visible here either. Callers must then render no minimum at all
 * rather than a stand-in.
 */
object ThorchainMemoLimit {

    /**
     * THORNode parses the LIM as a `cosmos.Uint` and rejects a memo whose value does not fit 256
     * bits. A wider number is a memo the chain refuses outright — no swap, and therefore no floor
     * to read a guarantee out of. Exclusive upper bound.
     */
    private val LIMIT_UPPER_BOUND: BigInteger = BigInteger.valueOf(2).pow(256)

    /**
     * A non-empty run of ASCII decimal digits. `Char.isDigit` alone also accepts non-ASCII
     * numerals, which `BigInteger` would then read as something the node never wrote.
     */
    private val DECIMAL_DIGITS = Regex("^[0-9]+$")

    /**
     * The minimum output [memo] asserts, in THORChain's own 1e8 fixed point — the same units as
     * `expected_amount_out`, so [com.vultisig.wallet.data.repositories.swap.convertToTokenValue]
     * rescales it into the destination coin's units.
     *
     * Returns null — never a guess — when the memo is not a swap memo, when any term of the
     * `LIM/INTERVAL/QUANTITY` triple is unreadable, or when the floor is `0` (what an omitted
     * `tolerance_bps` produces). A `=<` limit order also lands here: its action field is not a swap
     * action, and its displayed amount already IS the floor.
     */
    fun assertedLimit(memo: String): BigInteger? {
        val fields = memo.split(":")
        if (fields.size < 4 || !isSwapAction(fields[0])) return null

        // All-or-nothing on the whole triple, matching THORNode: it reads `LIM/INTERVAL/QUANTITY`
        // as one field and rejects the memo outright if any term is malformed, so a LIM salvaged
        // from a triple we could not fully read is a floor the chain would never enforce.
        val terms = fields[3].split("/")
        if (terms.size > 3) return null
        if (terms.drop(1).any { !DECIMAL_DIGITS.matches(it) }) return null
        if (!DECIMAL_DIGITS.matches(terms[0])) return null

        val limit = BigInteger(terms[0])
        if (limit.signum() <= 0 || limit >= LIMIT_UPPER_BOUND) return null
        return limit
    }

    /**
     * THORChain and MayaChain both spell the swap action `SWAP`, `=` or `s`, case-insensitively.
     * Every other action (`ADD`, `WITHDRAW`, `LOAN+`, the `=<` limit order …) lays its fields out
     * differently, so its 4th field is not a `LIM/INTERVAL/QUANTITY` triple and must not be read as
     * one.
     */
    private fun isSwapAction(field: String): Boolean =
        when (field.lowercase()) {
            "swap",
            "=",
            "s" -> true
            else -> false
        }
}
