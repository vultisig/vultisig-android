package com.vultisig.wallet.data.api.txstatus

import RippleBroadcastSuccessResponseJson
import RippleBroadcastSuccessResultJson
import RippleBroadcastSuccessTransactionJson
import RippleTxMetaJson
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class RippleStatusProviderTest {

    private val rippleApi = mockk<RippleApi>()
    private val provider = RippleStatusProvider(rippleApi)

    private fun response(validated: Boolean, transactionResult: String?) =
        RippleBroadcastSuccessResponseJson(
            result =
                RippleBroadcastSuccessResultJson(
                    hash = "h",
                    status = "success",
                    txJson =
                        RippleBroadcastSuccessTransactionJson(
                            account = "a",
                            deliverMax = "1",
                            destination = "d",
                            fee = "10",
                            flags = 0,
                            lastLedgerSequence = 0,
                            sequence = 0,
                            signingPubKey = "k",
                            transactionType = "Payment",
                            txnSignature = "s",
                        ),
                    validated = validated,
                    meta = transactionResult?.let { RippleTxMetaJson(transactionResult = it) },
                )
        )

    @Test
    fun `validated tesSUCCESS returns Confirmed`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } returns response(true, "tesSUCCESS")

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("h", Chain.Ripple))
    }

    @Test
    fun `validated tec failure returns Failed with the code`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } returns response(true, "tecUNFUNDED_PAYMENT")

        assertEquals(
            TransactionResult.Failed("tecUNFUNDED_PAYMENT"),
            provider.checkStatus("h", Chain.Ripple),
        )
    }

    @Test
    fun `unvalidated tesSUCCESS keeps polling (Pending), not terminal Confirmed`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } returns response(false, "tesSUCCESS")

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Ripple))
    }

    @Test
    fun `validated but missing meta returns Pending, not Confirmed`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } returns response(true, null)

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Ripple))
    }

    @Test
    fun `null response returns Pending`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } returns null

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Ripple))
    }

    @Test
    fun `api exception returns Pending`() = runTest {
        coEvery { rippleApi.getTsStatus(any()) } throws RuntimeException("net")

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Ripple))
    }

    /**
     * A validated OfferCreate `tx` response carries no `Destination`/`DeliverMax`/`Flags` in its
     * `tx_json` (those are Payment-only). This must still deserialize — otherwise the strict
     * decoder throws `MissingFieldException`, `getTsStatus` propagates it, and the status is
     * reported `Pending` forever even though the co-signed dApp offer settled `tesSUCCESS`.
     */
    @Test
    fun `validated OfferCreate tx_json without Payment fields deserializes`() {
        val json = Json { ignoreUnknownKeys = true }
        // Trimmed from a real xrplcluster `tx` response for a co-signed OfferCreate.
        val raw =
            """{"result":{"hash":"57064A9","status":"success","validated":true,""" +
                """"meta":{"TransactionResult":"tesSUCCESS"},""" +
                """"tx_json":{"Account":"rsFTwHnK6RxhGdM2FRKCey9fvLTg5tonsD","Fee":"20",""" +
                """"Sequence":92804008,"TakerGets":"1000000",""" +
                """"TakerPays":{"currency":"USD","issuer":"rvYAfWj","value":"2.5"},""" +
                """"TransactionType":"OfferCreate","TxnSignature":"3045","ledger_index":105633235}}}"""

        val parsed = json.decodeFromString<RippleBroadcastSuccessResponseJson>(raw)

        assertEquals(true, parsed.result.validated)
        assertEquals("tesSUCCESS", parsed.result.meta?.transactionResult)
        assertEquals("OfferCreate", parsed.result.txJson?.transactionType)
    }
}
