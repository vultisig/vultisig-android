package com.vultisig.wallet.data.api

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class SolanaBroadcastActionTest {

    private val maxAttempts = 3

    @Test
    fun `blockhash not found with retries left resends`() {
        assertEquals(
            SolanaBroadcastAction.RESEND,
            solanaBroadcastAction("Blockhash not found", attempt = 1, maxAttempts = maxAttempts),
        )
        assertEquals(
            SolanaBroadcastAction.RESEND,
            solanaBroadcastAction("Blockhash not found", attempt = 2, maxAttempts = maxAttempts),
        )
    }

    @Test
    fun `blockhash not found on the final attempt is treated as expired`() {
        assertEquals(
            SolanaBroadcastAction.EXPIRED,
            solanaBroadcastAction("Blockhash not found", attempt = 3, maxAttempts = maxAttempts),
        )
    }

    @Test
    fun `block height exceeded is expired and never resent`() {
        assertEquals(
            SolanaBroadcastAction.EXPIRED,
            solanaBroadcastAction(
                "Transaction simulation failed: block height exceeded",
                attempt = 1,
                maxAttempts = maxAttempts,
            ),
        )
    }

    @Test
    fun `other rpc errors are fatal`() {
        assertEquals(
            SolanaBroadcastAction.FATAL,
            solanaBroadcastAction(
                "Transaction simulation failed: insufficient funds for rent",
                attempt = 1,
                maxAttempts = maxAttempts,
            ),
        )
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(
            SolanaBroadcastAction.RESEND,
            solanaBroadcastAction("BLOCKHASH NOT FOUND", attempt = 1, maxAttempts = maxAttempts),
        )
        assertEquals(
            SolanaBroadcastAction.EXPIRED,
            solanaBroadcastAction("BLOCK HEIGHT EXCEEDED", attempt = 1, maxAttempts = maxAttempts),
        )
    }

    // The real sendTransaction error body carries the failure as the camelCase TransactionError
    // enum nested in data.err, not as the spaced human-readable phrase.
    @Test
    fun `camelCase BlockhashNotFound from the raw error object resends`() {
        val rawError =
            """{"code":-32002,"message":"Transaction simulation failed","data":{"err":"BlockhashNotFound","logs":[]}}"""
        assertEquals(
            SolanaBroadcastAction.RESEND,
            solanaBroadcastAction(rawError, attempt = 1, maxAttempts = maxAttempts),
        )
        assertEquals(
            SolanaBroadcastAction.EXPIRED,
            solanaBroadcastAction(rawError, attempt = 3, maxAttempts = maxAttempts),
        )
    }
}
