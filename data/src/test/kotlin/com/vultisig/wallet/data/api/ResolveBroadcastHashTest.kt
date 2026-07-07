package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseResultJson
import RippleBroadcastResponseResponseTransactionJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [resolveBroadcastHash] — the XRPL `submit` engine-result classifier.
 *
 * Covers the failure paths from issue #5185: a genuine rejection must surface a typed
 * [RippleBroadcastException] with the real reason, never the engine message returned as a fake
 * transaction hash. Duplicate-broadcast races (`tefPAST_SEQ` / `tefALREADY`) resolve by the echoed
 * hash so on-chain recovery verifies without re-broadcasting.
 */
class ResolveBroadcastHashTest {

    private fun result(engineResult: String, message: String? = null, hash: String? = null) =
        RippleBroadcastResponseResponseResultJson(
            engineResult = engineResult,
            engineResultMessage = message,
            txJson = RippleBroadcastResponseResponseTransactionJson(hash = hash),
        )

    @Test
    fun `tesSUCCESS returns echoed hash`() {
        val hash =
            resolveBroadcastHash(result("tesSUCCESS", "The transaction was applied.", "HASH1"))
        assertEquals("HASH1", hash)
    }

    @Test
    fun `tefPAST_SEQ duplicate resolves by hash`() {
        val hash =
            resolveBroadcastHash(
                result("tefPAST_SEQ", "This sequence number has already passed.", "HASH2")
            )
        assertEquals("HASH2", hash)
    }

    @Test
    fun `tefALREADY duplicate resolves by hash`() {
        val hash =
            resolveBroadcastHash(
                result("tefALREADY", "The transaction is already in this ledger.", "HASH3")
            )
        assertEquals("HASH3", hash)
    }

    @Test
    fun `already-applied message resolves by hash even when code is not recognized`() {
        val hash = resolveBroadcastHash(result("terRETRY", "The transaction was applied.", "HASH4"))
        assertEquals("HASH4", hash)
    }

    @Test
    fun `tem malformed rejection throws typed error and never returns a hash`() {
        val error =
            assertThrows<RippleBroadcastException> {
                resolveBroadcastHash(result("temBAD_FEE", "Fee must be positive.", hash = null))
            }
        assertEquals("temBAD_FEE", error.engineResult)
        assertEquals("Fee must be positive.", error.engineResultMessage)
        assertEquals("Ripple broadcast failed (temBAD_FEE): Fee must be positive.", error.message)
    }

    @Test
    fun `tec claimed-cost failure throws even when a hash is echoed`() {
        val error =
            assertThrows<RippleBroadcastException> {
                resolveBroadcastHash(
                    result("tecUNFUNDED_PAYMENT", "Insufficient XRP balance.", "ONCHAINHASH")
                )
            }
        assertEquals("tecUNFUNDED_PAYMENT", error.engineResult)
    }

    @Test
    fun `tef rejection other than a duplicate throws`() {
        val error =
            assertThrows<RippleBroadcastException> {
                resolveBroadcastHash(
                    result("tefMAX_LEDGER", "Ledger sequence too high.", hash = null)
                )
            }
        assertEquals("tefMAX_LEDGER", error.engineResult)
    }

    @Test
    fun `success with blank hash throws instead of inventing a hash`() {
        val error =
            assertThrows<RippleBroadcastException> {
                resolveBroadcastHash(
                    result("tesSUCCESS", "The transaction was applied.", hash = "")
                )
            }
        assertEquals("tesSUCCESS", error.engineResult)
    }

    @Test
    fun `failure without message falls back to code-only description`() {
        val error =
            assertThrows<RippleBroadcastException> {
                resolveBroadcastHash(result("telINSUF_FEE_P", message = null, hash = null))
            }
        assertEquals("Ripple broadcast failed (telINSUF_FEE_P)", error.message)
    }
}
