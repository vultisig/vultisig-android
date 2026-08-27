package com.vultisig.wallet.data.swap

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the floor the swap screens are allowed to call a minimum to the `LIM` the signed memo
 * actually carries (#5711). Every "unreadable" case must resolve to null rather than a salvaged
 * number: showing no minimum is the safe direction for a label that promises one.
 */
internal class ThorchainMemoLimitTest {

    @Test
    fun `reads the limit out of a memo that carries one`() {
        assertEquals(
            BigInteger("31678"),
            ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678:va:40"),
        )
    }

    @Test
    fun `reads the limit out of a streaming triple`() {
        assertEquals(
            BigInteger("31678"),
            ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678/1/0:va:40"),
        )
    }

    @Test
    fun `auto slippage memo asserts no limit`() {
        // The memo from #5711: RUNE -> TCY on Auto slippage. The LIM field is empty, so the swap
        // settled 0.37% under the number the screen was calling a minimum.
        assertNull(ThorchainMemoLimit.assertedLimit("=:THOR.TCY:thor1uet6qz79tu::va:40"))
    }

    @Test
    fun `an explicit zero limit asserts no limit`() {
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:0:va:40"))
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:0/1/0:va:40"))
    }

    @Test
    fun `accepts every spelling of the swap action`() {
        val limit = BigInteger("31678")
        assertEquals(limit, ThorchainMemoLimit.assertedLimit("SWAP:ETH.ETH:0xabc:31678"))
        assertEquals(limit, ThorchainMemoLimit.assertedLimit("swap:ETH.ETH:0xabc:31678"))
        assertEquals(limit, ThorchainMemoLimit.assertedLimit("s:ETH.ETH:0xabc:31678"))
        assertEquals(limit, ThorchainMemoLimit.assertedLimit("S:ETH.ETH:0xabc:31678"))
    }

    @Test
    fun `a non-swap action lays its fields out differently and is refused`() {
        // ADD's 4th field is not a LIM triple — reading one out of it would invent a floor.
        assertNull(ThorchainMemoLimit.assertedLimit("ADD:BTC.BTC:thor1abc:31678"))
        assertNull(ThorchainMemoLimit.assertedLimit("WITHDRAW:BTC.BTC:5000:31678"))
    }

    @Test
    fun `a limit order memo is not read as a market floor`() {
        // `=<` orders display their own target as the amount, so there is no second floor to show.
        assertNull(ThorchainMemoLimit.assertedLimit("=<:THOR.TCY:thor1abc:1000/7200/0:va:40"))
    }

    @Test
    fun `a memo with too few fields is refused`() {
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc"))
        assertNull(ThorchainMemoLimit.assertedLimit(""))
    }

    @Test
    fun `a triple with an unreadable term is refused whole`() {
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678/abc/0:va:40"))
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678/1/0/9:va:40"))
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678/:va:40"))
    }

    @Test
    fun `a non-integer limit is refused`() {
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678.5:va:40"))
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:-31678:va:40"))
        // Non-ASCII numerals BigInteger would never have been given by the node.
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:٣١٦٧٨:va:40"))
    }

    @Test
    fun `scientific notation is refused because this app never signs it`() {
        // Android signs the node's memo verbatim — no OP_RETURN compression — so an `e` mantissa is
        // a memo we did not produce and the node did not return.
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:31678e3:va:40"))
    }

    @Test
    fun `a limit wider than the node's own Uint is refused`() {
        val tooWide = BigInteger.valueOf(2).pow(256)
        assertNull(ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:$tooWide:va:40"))
        assertEquals(
            tooWide - BigInteger.ONE,
            ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:${tooWide - BigInteger.ONE}:va:40"),
        )
    }

    @Test
    fun `zero padding means what it says`() {
        // THORNode reads every numeric memo term at base 10, where `0031678` is 31678 — refusing it
        // here would hide a floor the network would have enforced.
        assertEquals(
            BigInteger("31678"),
            ThorchainMemoLimit.assertedLimit("=:ETH.ETH:0xabc:0031678:va:40"),
        )
    }
}
