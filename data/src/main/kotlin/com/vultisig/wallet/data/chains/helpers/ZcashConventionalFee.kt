package com.vultisig.wallet.data.chains.helpers

/**
 * ZIP-317 conventional fee for a transparent-only Zcash transaction. Nodes relay zero "unpaid
 * actions" by default, so any tx paying less is rejected at broadcast with "tx unpaid action limit
 * exceeded".
 *
 * Byte-parity port of the SDK's canonical implementation
 * (`packages/core/chain/chains/utxo/fee/zip317.ts`): every co-signing device must derive the same
 * fee from the same transaction shape, or the MPC preimage digests diverge and keysign fails. Keep
 * the math in lockstep with the SDK (and the iOS `ZcashConventionalFee`) when touching this file.
 *
 * https://zips.z.cash/zip-0317
 */
internal object ZcashConventionalFee {
    private const val MARGINAL_FEE: Long = 5_000L
    private const val GRACE_ACTIONS: Long = 2L
    /** Serialized size of a signed transparent P2PKH input (ZIP-317 section 3.1). */
    private const val P2PKH_INPUT_SIZE: Long = 148L
    private const val INPUT_ACTION_SIZE: Long = 150L
    private const val OUTPUT_ACTION_SIZE: Long = 34L
    /** Serialized tx_out size of a P2PKH output: 8 value + 1 scriptLen + 25 script. */
    private const val P2PKH_OUTPUT_SIZE: Long = 34L

    /** Ceiling division: smallest n such that n * divisor >= value. */
    fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor

    /**
     * Minimum fee the Zcash network relays for a transparent tx of the given shape: 5,000 zats per
     * logical action with a two-action grace window, where logical actions = max(ceil(tx_in bytes /
     * 150), ceil(tx_out bytes / 34)).
     */
    fun conventionalFee(inputCount: Int, outputSizes: List<Long>): Long {
        val inputActions = ceilDiv(inputCount.toLong() * P2PKH_INPUT_SIZE, INPUT_ACTION_SIZE)
        val outputActions = ceilDiv(outputSizes.sum(), OUTPUT_ACTION_SIZE)
        val logicalActions = maxOf(inputActions, outputActions)
        return MARGINAL_FEE * maxOf(GRACE_ACTIONS, logicalActions)
    }

    /**
     * Serialized tx_out size of an OP_RETURN output carrying [memoSize] bytes: 8 value + scriptLen
     * CompactSize + script (OP_RETURN + push opcode(s) + data). WalletCore's planner sizes this
     * output as a flat ~34 bytes regardless of memo length, so longer memos make its plan
     * undercount ZIP-317 actions. Models the push opcode (direct / PUSHDATA1 / PUSHDATA2 /
     * PUSHDATA4) and the script-length CompactSize so long memos are not undercharged.
     */
    fun opReturnOutputSize(memoSize: Int): Long {
        val dataSize = memoSize.toLong()

        // OP_RETURN (1 byte) + push opcode bytes for `dataSize`.
        val pushOverhead: Long =
            when {
                dataSize <= 75L -> 2L
                dataSize <= 0xffL -> 3L
                dataSize <= 0xffffL -> 4L
                else -> 6L
            }
        val scriptSize = pushOverhead + dataSize

        // CompactSize encoding of the script length prefix.
        val scriptLengthSize: Long =
            when {
                scriptSize < 0xfdL -> 1L
                scriptSize <= 0xffffL -> 3L
                scriptSize <= 0xffff_ffffL -> 5L
                else -> 9L
            }

        return 8L + scriptLengthSize + scriptSize
    }

    /**
     * Serialized tx_out sizes for a transparent Zcash send: recipient P2PKH, optional change P2PKH
     * (only when change is positive), and an optional OP_RETURN memo (only when [memoSize] is
     * positive). Feed into [conventionalFee] to size the conventional fee by real bytes.
     */
    fun transparentOutputSizes(change: Long, memoSize: Int): List<Long> {
        val sizes = mutableListOf(P2PKH_OUTPUT_SIZE)
        if (change > 0L) {
            sizes.add(P2PKH_OUTPUT_SIZE)
        }
        if (memoSize > 0) {
            sizes.add(opReturnOutputSize(memoSize))
        }
        return sizes
    }
}
