package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.models.SolanaRpcResponseJson
import com.vultisig.wallet.data.api.models.SolanaSignatureStatus
import com.vultisig.wallet.data.api.models.SolanaSignatureStatusesResult
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class SolanaStatusProviderTest {

    private val solanaApi = mockk<SolanaApi>()
    private val provider = SolanaStatusProvider(solanaApi)

    private fun response(status: SolanaSignatureStatus?) =
        SolanaRpcResponseJson(
            id = 1,
            result = SolanaSignatureStatusesResult(value = listOf(status)),
            error = null,
        )

    @Test
    fun `finalized without err returns Confirmed`() = runTest {
        coEvery { solanaApi.checkStatus(any()) } returns
            response(SolanaSignatureStatus(confirmationStatus = "finalized"))

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("h", Chain.Solana))
    }

    @Test
    fun `finalized with err returns Failed (reverted swap must not show green)`() = runTest {
        val err = Json.parseToJsonElement("""{"InstructionError":[0,{"Custom":6001}]}""")
        coEvery { solanaApi.checkStatus(any()) } returns
            response(SolanaSignatureStatus(confirmationStatus = "finalized", err = err))

        assertEquals(TransactionResult.Failed(err.toString()), provider.checkStatus("h", Chain.Solana))
    }

    @Test
    fun `err set while only processed still returns Failed`() = runTest {
        val err = JsonPrimitive("AccountInUse")
        coEvery { solanaApi.checkStatus(any()) } returns
            response(SolanaSignatureStatus(confirmationStatus = "processed", err = err))

        assertEquals(TransactionResult.Failed(err.toString()), provider.checkStatus("h", Chain.Solana))
    }

    @Test
    fun `processed without err returns Pending`() = runTest {
        coEvery { solanaApi.checkStatus(any()) } returns
            response(SolanaSignatureStatus(confirmationStatus = "processed"))

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Solana))
    }

    @Test
    fun `null status value returns Pending`() = runTest {
        coEvery { solanaApi.checkStatus(any()) } returns response(null)

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Solana))
    }

    @Test
    fun `api exception returns Pending`() = runTest {
        coEvery { solanaApi.checkStatus(any()) } throws RuntimeException("net")

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Solana))
    }
}
