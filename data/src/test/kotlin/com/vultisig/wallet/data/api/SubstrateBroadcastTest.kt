package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionErrorJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for the shared Substrate `author_submitExtrinsic` classifier used by both
 * [PolkadotApiImp] and [BittensorApiImp]. Guards the fix for the `(null, null)` malformed-body hole
 * where a body with neither `result` nor `error` was reported to the user as a successful
 * broadcast.
 */
class SubstrateBroadcastTest {

    private fun error(code: Int, message: String? = null, data: String? = null) =
        PolkadotBroadcastTransactionErrorJson(code = code, message = message, data = data)

    @Test
    fun `accepts a present result and returns the hash`() {
        val response = PolkadotBroadcastTransactionJson(result = "0xcafe", error = null)

        assertEquals("0xcafe", SubstrateBroadcast.classify(response))
    }

    @Test
    fun `treats an unset (null, null) body as unknown and throws`() {
        val response = PolkadotBroadcastTransactionJson(result = null, error = null)

        assertThrows<SubstrateBroadcastException> { SubstrateBroadcast.classify(response) }
    }

    @Test
    fun `idempotent code 1012 resolves to null`() {
        val response =
            PolkadotBroadcastTransactionJson(
                result = null,
                error = error(1012, "Priority is too low"),
            )

        assertNull(SubstrateBroadcast.classify(response))
    }

    @Test
    fun `idempotent code 1013 resolves to null`() {
        val response = PolkadotBroadcastTransactionJson(result = null, error = error(1013))

        assertNull(SubstrateBroadcast.classify(response))
    }

    @Test
    fun `case-variant already imported message resolves to null`() {
        val response =
            PolkadotBroadcastTransactionJson(result = null, error = error(6666, "ALREADY imported"))

        assertNull(SubstrateBroadcast.classify(response))
    }

    @Test
    fun `already known message resolves to null`() {
        val response =
            PolkadotBroadcastTransactionJson(
                result = null,
                error = error(6666, "Transaction Already Known"),
            )

        assertNull(SubstrateBroadcast.classify(response))
    }

    @Test
    fun `temporarily banned message resolves to null`() {
        val response =
            PolkadotBroadcastTransactionJson(
                result = null,
                error = error(6666, "Temporarily Banned"),
            )

        assertNull(SubstrateBroadcast.classify(response))
    }

    @Test
    fun `non-idempotent node error throws`() {
        val response =
            PolkadotBroadcastTransactionJson(
                result = null,
                error = error(1010, "Invalid Transaction"),
            )

        assertThrows<SubstrateBroadcastException> { SubstrateBroadcast.classify(response) }
    }
}
