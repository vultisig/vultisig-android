package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Golden vectors mirrored from the SDK's `zip317.test.ts`
 * (`packages/core/chain/chains/utxo/fee/zip317.test.ts`) and the iOS `ZcashConventionalFeeTests` —
 * the three implementations must stay byte-identical for MPC co-signing.
 */
class ZcashConventionalFeeTest {

    private val p2pkhOutput = 34L

    // conventionalFee

    @Test
    fun `returns the ten-thousand-zat floor for a simple one-in two-out send`() {
        assertEquals(
            10_000L,
            ZcashConventionalFee.conventionalFee(
                inputCount = 1,
                outputSizes = listOf(p2pkhOutput, p2pkhOutput),
            ),
        )
    }

    @Test
    fun `scales with input count beyond the grace window`() {
        assertEquals(
            20_000L,
            ZcashConventionalFee.conventionalFee(
                inputCount = 4,
                outputSizes = listOf(p2pkhOutput, p2pkhOutput),
            ),
        )
    }

    @Test
    fun `charges input actions from serialized bytes not raw count`() {
        // 75 P2PKH inputs: ceil(75 * 148 / 150) = 74 actions, not 75.
        assertEquals(
            370_000L,
            ZcashConventionalFee.conventionalFee(
                inputCount = 75,
                outputSizes = listOf(p2pkhOutput, p2pkhOutput),
            ),
        )
    }

    @Test
    fun `counts large OP_RETURN outputs as multiple actions`() {
        // 80-byte memo output: 92 bytes serialized -> with two p2pkh outputs,
        // ceil(160 / 34) = 5 actions -> 25,000 zats.
        assertEquals(
            25_000L,
            ZcashConventionalFee.conventionalFee(
                inputCount = 1,
                outputSizes = listOf(p2pkhOutput, p2pkhOutput, 92L),
            ),
        )
    }

    // opReturnOutputSize

    @Test
    fun `sizes a short memo with a single-byte push`() {
        // 9 fixed bytes + 2 push overhead + data length.
        assertEquals(51L, ZcashConventionalFee.opReturnOutputSize(40))
    }

    @Test
    fun `adds a byte of push overhead once the memo exceeds 75 bytes`() {
        assertEquals(86L, ZcashConventionalFee.opReturnOutputSize(75))
        assertEquals(88L, ZcashConventionalFee.opReturnOutputSize(76))
    }

    @Test
    fun `handles CompactSize and PUSHDATA boundaries for long memos`() {
        // 250-byte memo: script length 253 crosses the CompactSize 1->3 byte threshold.
        assertEquals(261L, ZcashConventionalFee.opReturnOutputSize(249))
        assertEquals(264L, ZcashConventionalFee.opReturnOutputSize(250))
        // 256-byte memo: push opcode crosses PUSHDATA1 -> PUSHDATA2.
        assertEquals(269L, ZcashConventionalFee.opReturnOutputSize(255))
        assertEquals(271L, ZcashConventionalFee.opReturnOutputSize(256))
    }

    @Test
    fun `handles upper CompactSize and PUSHDATA boundaries`() {
        // Script length 65535 -> 65536 crosses the CompactSize 3 -> 5 byte threshold
        // (memo 65531 -> script 65535; memo 65532 -> script 65536).
        assertEquals(65_546L, ZcashConventionalFee.opReturnOutputSize(65_531))
        assertEquals(65_549L, ZcashConventionalFee.opReturnOutputSize(65_532))
        // Memo 65535 -> 65536 crosses the PUSHDATA2 -> PUSHDATA4 threshold (push overhead 4 -> 6).
        assertEquals(65_552L, ZcashConventionalFee.opReturnOutputSize(65_535))
        assertEquals(65_555L, ZcashConventionalFee.opReturnOutputSize(65_536))
    }

    // transparentOutputSizes

    @Test
    fun `returns recipient only when there is no change and no memo`() {
        assertEquals(
            listOf(34L),
            ZcashConventionalFee.transparentOutputSizes(change = 0L, memoSize = 0),
        )
    }

    @Test
    fun `adds a change output only when change is positive`() {
        assertEquals(
            listOf(34L, 34L),
            ZcashConventionalFee.transparentOutputSizes(change = 1L, memoSize = 0),
        )
    }

    @Test
    fun `appends the OP_RETURN size for a memo send`() {
        assertEquals(
            listOf(34L, 34L, 51L),
            ZcashConventionalFee.transparentOutputSizes(change = 1L, memoSize = 40),
        )
    }

    // ceilDiv

    @Test
    fun `ceilDiv rounds up to the smallest clearing multiple`() {
        assertEquals(0L, ZcashConventionalFee.ceilDiv(0L, 34L))
        assertEquals(1L, ZcashConventionalFee.ceilDiv(34L, 34L))
        assertEquals(2L, ZcashConventionalFee.ceilDiv(35L, 34L))
        assertEquals(77L, ZcashConventionalFee.ceilDiv(20_000L, 260L))
    }
}
