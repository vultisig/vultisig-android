package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `triggerconstantcontract` answers a doomed transfer with an ordinary HTTP 200 whose energy
 * figures are far below what the real send costs, so consuming one produces a signed `fee_limit`
 * that guarantees OUT_OF_ENERGY at broadcast. These tests pin the rejection of such a response and
 * the node the simulation runs against.
 */
class TronApiFeeSimulationTest {

    private fun newApi(
        body: String,
        capture: MockHttpClient.RequestCapture = MockHttpClient.RequestCapture(),
    ): TronApi =
        TronApiImpl(httpClient = MockHttpClient.capturingRequest(HttpStatusCode.OK, body, capture))

    private suspend fun TronApi.simulate() =
        getTriggerConstantContractFee(
            ownerAddressBase58 = OWNER,
            contractAddressBase58 = CONTRACT,
            recipientAddressHex = RECIPIENT_HEX,
            functionSelector = TronApiImpl.TRANSFER_FUNCTION_SELECTOR,
            amount = BigInteger.valueOf(1_000_000L),
        )

    @Test
    fun `a clean simulation returns its energy figures`() = runBlocking {
        val api = newApi(simulationBody(result = "true", energyUsed = 31_895, energyPenalty = 0))

        val result = api.simulate()

        assertEquals(31_895L, result.energyUsed)
        assertEquals(0L, result.energyPenalty)
    }

    @Test
    fun `a revert carrying result true is rejected`() {
        // The shape TronGrid actually returns for a doomed transfer: the call itself succeeded, so
        // `result` is true, and only the message says the contract reverted.
        val api =
            newApi(
                simulationBody(
                    result = "true",
                    energyUsed = 1_080,
                    energyPenalty = 0,
                    message = "REVERT opcode executed",
                )
            )

        val error = assertThrows<IllegalStateException> { runBlocking { api.simulate() } }

        assertTrue(error.message.orEmpty().contains("REVERT opcode executed"))
    }

    @Test
    fun `a simulation carrying an error code is rejected`() {
        val api =
            newApi(
                simulationBody(
                    result = "true",
                    energyUsed = 1_080,
                    energyPenalty = 0,
                    code = "CONTRACT_VALIDATE_ERROR",
                )
            )

        val error = assertThrows<IllegalStateException> { runBlocking { api.simulate() } }

        assertTrue(error.message.orEmpty().contains("CONTRACT_VALIDATE_ERROR"))
    }

    @Test
    fun `a simulation reporting result false is rejected`() {
        val api = newApi(simulationBody(result = "false", energyUsed = 31_895, energyPenalty = 0))

        assertThrows<IllegalStateException> { runBlocking { api.simulate() } }
    }

    @Test
    fun `a FAILED transaction ret is rejected`() {
        val api =
            newApi(
                simulationBody(
                    result = "true",
                    energyUsed = 31_895,
                    energyPenalty = 0,
                    ret = "FAILED",
                )
            )

        assertThrows<IllegalStateException> { runBlocking { api.simulate() } }
    }

    @Test
    fun `a non-positive energy estimate is rejected`() {
        // Zero energy would serialize as feeLimit = 0, which cannot pay for any TRC-20 transfer.
        val api = newApi(simulationBody(result = "true", energyUsed = 0, energyPenalty = 0))

        assertThrows<IllegalStateException> { runBlocking { api.simulate() } }
    }

    @Test
    fun `the simulation runs on the full node, not the solidified one`() {
        // walletsolidity lags the head by ~60s, so a just-funded account simulates against state
        // that does not have its balance yet.
        val capture = MockHttpClient.RequestCapture()
        val api =
            newApi(simulationBody(result = "true", energyUsed = 31_895, energyPenalty = 0), capture)

        runBlocking { api.simulate() }

        assertEquals("/tron/wallet/triggerconstantcontract", capture.lastPath)
    }

    private fun simulationBody(
        result: String,
        energyUsed: Int,
        energyPenalty: Int,
        code: String? = null,
        message: String? = null,
        ret: String? = null,
    ): String {
        val codeField = code?.let { ""","code": "$it"""" }.orEmpty()
        val messageField = message?.let { ""","message": "$it"""" }.orEmpty()
        val retItem = ret?.let { """{ "ret": "$it" }""" } ?: "{}"
        return """
            {
              "result": { "result": $result$codeField$messageField },
              "energy_used": $energyUsed,
              "energy_penalty": $energyPenalty,
              "transaction": {
                "ret": [$retItem],
                "raw_data_hex": "0a02b1f7"
              }
            }
            """
            .trimIndent()
    }

    private companion object {
        const val OWNER = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        const val CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val RECIPIENT_HEX = "0x41e552f6487585c2b58bc2c9bb4492bc1f17132cd0"
    }
}
