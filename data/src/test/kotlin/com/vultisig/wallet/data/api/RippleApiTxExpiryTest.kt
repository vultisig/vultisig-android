package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Response bodies are trimmed from live xrplcluster `tx` answers to a ledger-bounded lookup. */
class RippleApiTxExpiryTest {

    private val capture = MockHttpClient.RequestCapture()

    private fun newApi(body: String): RippleApi =
        RippleApiImp(
            http = MockHttpClient.capturingRequest(HttpStatusCode.OK, body, capture),
            TestTimeSource(),
        )

    private fun notFound(searchedAll: Boolean) =
        """{"result":{"error":"txnNotFound","error_code":29,""" +
            """"error_message":"Transaction not found.","searched_all":$searchedAll,""" +
            """"status":"error"}}"""

    @Test
    fun `not found with every ledger through LastLedgerSequence searched is expired`() = runTest {
        assertTrue(newApi(notFound(true)).isExpiredPastLastLedger(HASH, 107_426_482))
    }

    @Test
    fun `not found while LastLedgerSequence is not validated yet is not expired`() = runTest {
        assertFalse(newApi(notFound(false)).isExpiredPastLastLedger(HASH, 107_426_542))
    }

    @Test
    fun `not found without searched_all is not expired`() = runTest {
        val body = """{"result":{"error":"txnNotFound","error_code":29,"status":"error"}}"""

        assertFalse(newApi(body).isExpiredPastLastLedger(HASH, 107_426_482))
    }

    @Test
    fun `a found transaction is not expired`() = runTest {
        val body =
            """{"result":{"hash":"$HASH","status":"success","validated":true,""" +
                """"meta":{"TransactionResult":"tesSUCCESS"}}}"""

        assertFalse(newApi(body).isExpiredPastLastLedger(HASH, 107_426_482))
    }

    @Test
    fun `searches the 1000 ledgers ending at LastLedgerSequence`() = runTest {
        newApi(notFound(true)).isExpiredPastLastLedger(HASH, 107_426_482)

        val params =
            Json.parseToJsonElement(capture.lastBody).jsonObject["params"]!!.jsonArray[0].jsonObject
        assertEquals(
            "tx",
            Json.parseToJsonElement(capture.lastBody).jsonObject["method"]!!.jsonPrimitive.content,
        )
        assertEquals(HASH, params["transaction"]!!.jsonPrimitive.content)
        assertEquals(107_425_483, params["min_ledger"]!!.jsonPrimitive.long)
        assertEquals(107_426_482, params["max_ledger"]!!.jsonPrimitive.long)
    }

    @Test
    fun `range start never drops below the first ledger`() = runTest {
        newApi(notFound(true)).isExpiredPastLastLedger(HASH, 40)

        val params =
            Json.parseToJsonElement(capture.lastBody).jsonObject["params"]!!.jsonArray[0].jsonObject
        assertEquals(1, params["min_ledger"]!!.jsonPrimitive.long)
        assertEquals(40, params["max_ledger"]!!.jsonPrimitive.long)
    }

    private companion object {
        const val HASH = "0330A54C7C954C36C1E78CC0659E28C227D8ADA38D5175B704F9C8434158778A"
    }
}
