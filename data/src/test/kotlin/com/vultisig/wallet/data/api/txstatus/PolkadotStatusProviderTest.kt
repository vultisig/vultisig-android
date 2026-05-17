package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.models.cosmos.PolkadotErrorData
import com.vultisig.wallet.data.api.models.cosmos.PolkadotExtrinsicDataJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotExtrinsicResponseJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PolkadotStatusProviderTest {

    private val polkadotApi = mockk<PolkadotApi>()
    private val provider = PolkadotStatusProvider(polkadotApi)

    @Test
    fun `maps subscan success response to Confirmed`() = runTest {
        coEvery { polkadotApi.getTxStatus(TX_HASH) } returns
            successResponse(extrinsicHash = TX_HASH)

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `maps null subscan response to NotFound`() = runTest {
        coEvery { polkadotApi.getTxStatus(TX_HASH) } returns null

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.NotFound, result)
    }

    @Test
    fun `maps on-chain failure to Failed with the error value`() = runTest {
        coEvery { polkadotApi.getTxStatus(TX_HASH) } returns
            PolkadotExtrinsicResponseJson(
                code = 0,
                message = "indexer reports error",
                generatedAt = 0,
                data =
                    PolkadotExtrinsicDataJson(
                        polkadotErrorData =
                            PolkadotErrorData(
                                batchIndex = 0,
                                doc = "",
                                module = "system",
                                name = "BadOrigin",
                                value = "BadOrigin: invalid origin",
                                version = 0,
                            ),
                        extrinsicHash = TX_HASH,
                    ),
            )

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Failed("BadOrigin: invalid origin"), result)
    }

    @Test
    fun `subscan API key rejection terminates polling with TimedOut`() = runTest {
        coEvery { polkadotApi.getTxStatus(TX_HASH) } throws
            NetworkException(
                httpStatusCode = 400,
                message =
                    "{\"code\": 403, \"message\": \"Subscan API strictly requires an API key. " +
                        "Unauthenticated access is disabled.\"}",
            )

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.TimedOut, result)
    }

    @Test
    fun `transient network errors keep polling alive as Pending`() = runTest {
        coEvery { polkadotApi.getTxStatus(TX_HASH) } throws
            NetworkException(httpStatusCode = 0, message = "Connection timed out")

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    private fun successResponse(extrinsicHash: String) =
        PolkadotExtrinsicResponseJson(
            code = 0,
            message = "success",
            generatedAt = 1_700_000_000,
            data =
                PolkadotExtrinsicDataJson(polkadotErrorData = null, extrinsicHash = extrinsicHash),
        )

    private companion object {
        const val TX_HASH = "0xdeadbeef"
    }
}
