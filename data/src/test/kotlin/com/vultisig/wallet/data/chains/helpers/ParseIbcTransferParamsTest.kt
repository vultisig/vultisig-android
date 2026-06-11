package com.vultisig.wallet.data.chains.helpers

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

internal class ParseIbcTransferParamsTest {

    private val validLatestBlock = "19000000_1714000000000000000"
    private val timeout = 1714000000000000000L

    @Test
    fun `parses a 3-part memo with no forward memo`() {
        val result =
            parseIbcTransferParams(
                memo = "cosmoshub:channel-141:osmo1dest",
                latestBlock = validLatestBlock,
            )

        assertEquals("channel-141", result.sourceChannel)
        assertEquals("", result.forwardMemo)
        assertEquals(timeout, result.timeoutTimestamp)
    }

    @Test
    fun `parses a 4-part memo with a forward memo`() {
        val result =
            parseIbcTransferParams(
                memo = "cosmoshub:channel-141:osmo1dest:fwd",
                latestBlock = validLatestBlock,
            )

        assertEquals("channel-141", result.sourceChannel)
        assertEquals("fwd", result.forwardMemo)
    }

    @Test
    fun `rejoins a forward memo that itself contains colons`() {
        val result =
            parseIbcTransferParams(
                memo = "cosmoshub:channel-141:osmo1dest:wasm:contract:call",
                latestBlock = validLatestBlock,
            )

        assertEquals("wasm:contract:call", result.forwardMemo)
    }

    @Test
    fun `throws when the memo is null`() {
        assertFailsWith<IllegalStateException> {
            parseIbcTransferParams(memo = null, latestBlock = validLatestBlock)
        }
    }

    @Test
    fun `throws when the source channel is missing or blank`() {
        assertFailsWith<IllegalArgumentException> {
            parseIbcTransferParams(memo = "cosmoshub::osmo1dest", latestBlock = validLatestBlock)
        }
        assertFailsWith<IllegalArgumentException> {
            parseIbcTransferParams(memo = "cosmoshub", latestBlock = validLatestBlock)
        }
    }

    @Test
    fun `throws when latestBlock is null`() {
        assertFailsWith<IllegalArgumentException> {
            parseIbcTransferParams(memo = "cosmoshub:channel-141:osmo1dest", latestBlock = null)
        }
    }

    @Test
    fun `throws when the timeout is zero or unparseable`() {
        assertFailsWith<IllegalArgumentException> {
            parseIbcTransferParams(
                memo = "cosmoshub:channel-141:osmo1dest",
                latestBlock = "19000000_0",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            parseIbcTransferParams(
                memo = "cosmoshub:channel-141:osmo1dest",
                latestBlock = "19000000_notanumber",
            )
        }
    }
}
