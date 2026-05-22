package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.BlockIdJson
import com.vultisig.wallet.data.api.models.BlockJson
import com.vultisig.wallet.data.api.models.CacaoProviderResponse
import com.vultisig.wallet.data.api.models.DataJson
import com.vultisig.wallet.data.api.models.HeaderJson
import com.vultisig.wallet.data.api.models.MayaLatestBlockInfoResponse
import com.vultisig.wallet.data.api.models.PartsJson
import com.vultisig.wallet.data.api.models.VersionJson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetMayaCacaoMaturityStatusUseCaseTest {

    private lateinit var mayaChainApi: MayaChainApi
    private lateinit var useCase: GetMayaCacaoMaturityStatusUseCaseImpl

    private val address = "maya1someaddress"

    @BeforeEach
    fun setUp() {
        mayaChainApi = mockk()
        useCase = GetMayaCacaoMaturityStatusUseCaseImpl(mayaChainApi)
    }

    @Test
    fun `isMature is true at boundary equality current equals lastDeposit plus depositMaturity`() =
        runTest {
            stubChain(currentHeight = 1_000_000L, lastDepositHeight = 500_000L, maturity = 500_000L)

            val status = useCase(address)

            assertTrue(status.isMature)
            assertEquals(0L, status.remainingBlocks)
            assertEquals(0L, status.remainingSeconds)
        }

    @Test
    fun `isMature is false one block before boundary`() = runTest {
        stubChain(currentHeight = 999_999L, lastDepositHeight = 500_000L, maturity = 500_000L)

        val status = useCase(address)

        assertFalse(status.isMature)
        assertEquals(1L, status.remainingBlocks)
        assertEquals(6L, status.remainingSeconds)
    }

    @Test
    fun `remainingSeconds uses 6 second block time`() = runTest {
        stubChain(currentHeight = 1_000L, lastDepositHeight = 1_000L, maturity = 100L)

        val status = useCase(address)

        assertFalse(status.isMature)
        assertEquals(100L, status.remainingBlocks)
        assertEquals(600L, status.remainingSeconds)
    }

    @Test
    fun `returns fail-closed isMature false on RPC error`() = runTest {
        coEvery { mayaChainApi.getLatestBlock() } throws RuntimeException("boom")
        coEvery { mayaChainApi.getMayaConstants() } returns
            mapOf("CACAOPOOLDEPOSITMATURITYBLOCKS" to 100L)
        coEvery { mayaChainApi.getCacaoProvider(address) } returns cacaoProvider(1_000L)

        val status = useCase(address)

        assertFalse(status.isMature)
        assertEquals(0L, status.remainingBlocks)
    }

    @Test
    fun `fails closed when mimir key is missing`() = runTest {
        coEvery { mayaChainApi.getLatestBlock() } returns latestBlock(1_000L)
        coEvery { mayaChainApi.getMayaConstants() } returns emptyMap()
        coEvery { mayaChainApi.getCacaoProvider(address) } returns cacaoProvider(1_000L)

        val status = useCase(address)

        assertFalse(status.isMature)
    }

    @Test
    fun `invoke with lastDepositHeight skips provider fetch and returns correct status`() =
        runTest {
            coEvery { mayaChainApi.getLatestBlock() } returns latestBlock(1_000_000L)
            coEvery { mayaChainApi.getMayaConstants() } returns
                mapOf("CACAOPOOLDEPOSITMATURITYBLOCKS" to 500_000L)

            val status = useCase(500_000L)

            assertTrue(status.isMature)
            assertEquals(0L, status.remainingBlocks)
            assertEquals(0L, status.remainingSeconds)
        }

    @Test
    fun `invoke with lastDepositHeight returns not-mature when blocks remain`() = runTest {
        coEvery { mayaChainApi.getLatestBlock() } returns latestBlock(999_999L)
        coEvery { mayaChainApi.getMayaConstants() } returns
            mapOf("CACAOPOOLDEPOSITMATURITYBLOCKS" to 500_000L)

        val status = useCase(500_000L)

        assertFalse(status.isMature)
        assertEquals(1L, status.remainingBlocks)
        assertEquals(6L, status.remainingSeconds)
    }

    @Test
    fun `invoke with lastDepositHeight returns UNKNOWN on RPC error`() = runTest {
        coEvery { mayaChainApi.getLatestBlock() } throws RuntimeException("boom")
        coEvery { mayaChainApi.getMayaConstants() } returns
            mapOf("CACAOPOOLDEPOSITMATURITYBLOCKS" to 500_000L)

        val status = useCase(500_000L)

        assertTrue(status.isUnknown)
        assertFalse(status.isMature)
    }

    private fun stubChain(currentHeight: Long, lastDepositHeight: Long, maturity: Long) {
        coEvery { mayaChainApi.getLatestBlock() } returns latestBlock(currentHeight)
        coEvery { mayaChainApi.getMayaConstants() } returns
            mapOf("CACAOPOOLDEPOSITMATURITYBLOCKS" to maturity)
        coEvery { mayaChainApi.getCacaoProvider(address) } returns cacaoProvider(lastDepositHeight)
    }

    private fun latestBlock(height: Long): MayaLatestBlockInfoResponse {
        val parts = PartsJson(total = 1, hash = "")
        val blockId = BlockIdJson(hash = "", parts = parts)
        val header =
            HeaderJson(
                version = VersionJson(block = "0"),
                chainId = "mayachain",
                height = height.toString(),
                time = "",
                lastBlockId = blockId,
                lastCommitHash = "",
                dataHash = "",
                validatorsHash = "",
                nextValidatorsHash = "",
                consensusHash = "",
                appHash = "",
                lastResultsHash = "",
                evidenceHash = "",
                proposerAddress = "",
            )
        return MayaLatestBlockInfoResponse(
            blockId = blockId,
            block = BlockJson(header = header, data = DataJson(txs = null)),
        )
    }

    private fun cacaoProvider(lastDepositHeight: Long): CacaoProviderResponse =
        CacaoProviderResponse(
            cacaoAddress = address,
            units = "0",
            value = "0",
            pnl = "0",
            depositAmount = "0",
            withdrawAmount = "0",
            lastDepositHeight = lastDepositHeight,
            lastWithdrawHeight = 0L,
        )
}
