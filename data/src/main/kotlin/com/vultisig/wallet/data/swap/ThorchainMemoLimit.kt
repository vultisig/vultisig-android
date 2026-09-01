package com.vultisig.wallet.data.swap

import java.math.BigInteger

/**
 * Reads the minimum-output floor (`LIM`) a THORChain / MayaChain swap memo asserts.
 *
 * The node bakes the floor into the memo it returns, and that memo is signed verbatim
 * (`SwapTransactionBuilder` passes `quote.data.memo` straight through, and `UtxoHelper` writes it
 * into the OP_RETURN unchanged). So the floor is read back out of the memo rather than re-derived
 * from the tolerance we sent: the node owns the rounding and the streaming split, and a client-side
 * re-derivation would drift from it.
 *
 * What the node applies the tolerance to is the swap's own emit — the full-size output before the
 * outbound fee and before the affiliate cut — not `expected_amount_out`, which is that emit with
 * both already taken off. Live quotes, one pair and amount, 3% tolerance:
 * ```
 * no affiliate       → LIM 872730873   expected_amount_out 898821632
 * affiliate va/30bps → LIM 872730873   expected_amount_out 896125362
 * affiliate va/10bps → LIM 872730873   expected_amount_out 897923005
 * ```
 *
 * The LIM does not move with the affiliate bps at all, and at a small tolerance it lands *above*
 * `expected_amount_out` — a live 50 bps quote returned LIM `31739612` against `expected_amount_out
 * 31657653`. So the LIM is the level the chain checks the swap against, not the amount that reaches
 * the user: the outbound fee and the affiliate cut still come off after that check.
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
     * to read a guarantee out of. Exclusive upper bound, shared with
     * [com.vultisig.wallet.data.swap.limit.LimitSwapMemo] so both memo grammars refuse the same
     * magnitudes the chain does.
     */
    internal val LIMIT_UPPER_BOUND: BigInteger = BigInteger.valueOf(2).pow(256)

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
     * The destination asset [memo] names, in THORChain's own notation (`THOR.TCY`,
     * `ETH.USDC-06EB48`, a secured `eth-usdc-0x…` denom), or null when [memo] is not a swap memo.
     *
     * Read from the same field layout [assertedLimit] validates, so a caller can never pair a limit
     * with the asset name of a different memo. The value is returned verbatim — resolving what it
     * refers to needs the coin, and belongs to the caller that holds one.
     */
    fun targetAsset(memo: String): String? {
        val fields = memo.split(":")
        if (fields.size < 2 || !isSwapAction(fields[0])) return null
        return fields[1].takeIf { it.isNotBlank() }
    }

    /**
     * THORChain and MayaChain both spell the swap action `SWAP`, `=` or `s`, case-insensitively.
     *
     * The action is the only thing keeping a `=<` limit order out of here: its 4th field *is* a
     * `LIM/INTERVAL/QUANTITY` triple at the same index — `LimitSwapMemo.build` emits
     * `=<:TARGET:DEST:LIM/INTERVAL/0` — so the field layout alone would read one happily. It must
     * not: a limit order's displayed destination amount already IS its LIM, so returning it here
     * would spell the same floor out a second time as if it were a market swap's minimum. The other
     * actions (`ADD`, `WITHDRAW`, `LOAN+` …) do lay their fields out differently, and their 4th
     * field is not a triple at all.
     */
    private fun isSwapAction(field: String): Boolean =
        when (field.lowercase()) {
            "swap",
            "=",
            "s" -> true
            else -> false
        }
}
