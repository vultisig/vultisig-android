package com.vultisig.wallet.data.api.txstatus

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Pins the revert-reason decoder against the shapes real nodes answer an `eth_call` replay with.
 *
 * The payloads are genuine `Error(string)` encodings — selector, offset word, length word, then the
 * UTF-8 bytes right-padded to a word — for the two reverts #5802 is about: LI.FI's "Insufficient
 * output" and 1inch/Kyber's "Return amount is not enough".
 */
class EvmRevertReasonTest {

    /** Wraps a bare hex payload the way a node's `error.data` carries it. */
    private fun decode(message: String?, data: String? = null): String? =
        EvmRevertReason.decode(message, data?.let(::JsonPrimitive))

    private val insufficientOutput =
        "0x08c379a0" +
            "0000000000000000000000000000000000000000000000000000000000000020" +
            "0000000000000000000000000000000000000000000000000000000000000013" +
            "496e73756666696369656e74206f757470757400000000000000000000000000"

    private val returnAmountNotEnough =
        "0x08c379a0" +
            "0000000000000000000000000000000000000000000000000000000000000020" +
            "000000000000000000000000000000000000000000000000000000000000001b" +
            "52657475726e20616d6f756e74206973206e6f7420656e6f7567680000000000"

    @Test
    fun `decodes an Error(string) payload from error data`() {
        assertEquals(
            "Insufficient output",
            decode(message = "execution reverted", data = insufficientOutput),
        )
    }

    @Test
    fun `decodes the 1inch min-return payload`() {
        assertEquals(
            "Return amount is not enough",
            decode(message = null, data = returnAmountNotEnough),
        )
    }

    @Test
    fun `reads the payload out of a nested error data object`() {
        val nested = Json.parseToJsonElement("""{"data":"$insufficientOutput","code":3}""")

        assertEquals("Insufficient output", EvmRevertReason.decode(message = null, data = nested))
    }

    @Test
    fun `reads the payload out of a wrapped originalError`() {
        val wrapped =
            Json.parseToJsonElement("""{"originalError":{"data":"$insufficientOutput"}}""")

        assertEquals("Insufficient output", EvmRevertReason.decode(message = null, data = wrapped))
    }

    @Test
    fun `falls back to the message when the node sends no payload`() {
        assertEquals(
            "Insufficient output",
            decode(message = "execution reverted: Insufficient output", data = null),
        )
    }

    @Test
    fun `decodes a payload the node put in the message instead of the data`() {
        assertEquals(
            "Insufficient output",
            decode(message = "execution reverted: $insufficientOutput", data = null),
        )
    }

    @Test
    fun `strips a ganache-style revert prefix`() {
        assertEquals(
            "Return amount is not enough",
            decode(
                message =
                    "VM Exception while processing transaction: revert Return amount is not enough",
                data = null,
            ),
        )
    }

    @Test
    fun `a payload beats a message that only says it reverted`() {
        assertEquals(
            "Insufficient output",
            EvmRevertReason.decode(
                message = "execution reverted",
                data = JsonPrimitive(insufficientOutput),
            ),
        )
    }

    @Test
    fun `a bare execution reverted carries no reason`() {
        assertNull(decode(message = "execution reverted", data = null))
        assertNull(decode(message = "execution reverted:", data = null))
    }

    /**
     * An `eth_call` that fails for its own reasons — no archive state for the block, a rate limit —
     * is not a revert reason. Storing one would put "missing trie node" where an explanation of the
     * user's failed swap belongs.
     */
    @Test
    fun `an unrelated RPC error is not read as a revert reason`() {
        assertNull(
            decode(
                message = "missing trie node 0xabc (path ) state 0xdef is not available",
                data = null,
            )
        )
        assertNull(decode(message = "rate limit exceeded", data = null))
    }

    @Test
    fun `null and blank inputs decode to null`() {
        assertNull(decode(message = null, data = null))
        assertNull(decode(message = "   ", data = ""))
    }

    @Test
    fun `a non-Error selector is left alone`() {
        // Panic(uint256) with code 0x11 (arithmetic overflow) — no string to recover.
        val panic =
            "0x4e487b71" + "0000000000000000000000000000000000000000000000000000000000000011"

        assertNull(decode(message = "execution reverted", data = panic))
    }

    @Test
    fun `a truncated payload decodes to null rather than throwing`() {
        assertNull(decode(message = "execution reverted", data = insufficientOutput.take(40)))
    }

    @Test
    fun `a length longer than the payload decodes to null`() {
        val overlong =
            "0x08c379a0" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "00000000000000000000000000000000000000000000000000000000000000ff" +
                "496e73756666696369656e74206f757470757400000000000000000000000000"

        assertNull(decode(message = null, data = overlong))
    }

    @Test
    fun `bytes that are not text decode to null`() {
        val binary =
            "0x08c379a0" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "0001020300000000000000000000000000000000000000000000000000000000"

        assertNull(decode(message = null, data = binary))
    }
}
