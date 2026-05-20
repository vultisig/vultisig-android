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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetCacaoUnstakeMaturityUseCaseTest {

    private lateinit var api: MayaChainApi
    private lateinit var useCase: GetCacaoUnstakeMaturityUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = GetCacaoUnstakeMaturityUseCaseImpl(api)
    }

    @Test
    fun `returns Mature when current block has passed the unlock block`() = runTest {
        givenChainState(lastDeposit = 1_000_000L, current = 1_500_000L, maturity = 302_400L)

        val result = useCase(ADDRESS)

        assertEquals(CacaoUnstakeMaturity.Mature, result)
    }

    @Test
    fun `returns Mature when current block exactly equals the unlock block`() = runTest {
        givenChainState(lastDeposit = 1_000_000L, current = 1_302_400L, maturity = 302_400L)

        val result = useCase(ADDRESS)

        assertEquals(CacaoUnstakeMaturity.Mature, result)
    }

    @Test
    fun `returns Locked with remaining block count when current is before unlock`() = runTest {
        givenChainState(lastDeposit = 1_000_000L, current = 1_200_000L, maturity = 302_400L)

        val result = useCase(ADDRESS)

        assertTrue(result is CacaoUnstakeMaturity.Locked)
        assertEquals(102_400L, result.remainingBlocks)
    }

    @Test
    fun `Locked remainingSeconds applies the 6s Maya block time`() = runTest {
        givenChainState(lastDeposit = 1_000_000L, current = 1_200_000L, maturity = 302_400L)

        val result = useCase(ADDRESS) as CacaoUnstakeMaturity.Locked

        assertEquals(102_400L * 6L, result.remainingSeconds)
    }

    @Test
    fun `returns Unknown when an API call throws`() = runTest {
        coEvery { api.getLatestBlock() } throws RuntimeException("network down")
        coEvery { api.getMayaConstants() } returns mapOf(MATURITY_KEY to 302_400L)
        coEvery { api.getCacaoProvider(ADDRESS) } returns cacaoProvider(lastDepositHeight = 1L)

        val result = useCase(ADDRESS)

        assertEquals(CacaoUnstakeMaturity.Unknown, result)
    }

    @Test
    fun `rethrows CancellationException without swallowing it`() = runTest {
        coEvery { api.getLatestBlock() } throws CancellationException("cancelled")
        coEvery { api.getMayaConstants() } returns mapOf(MATURITY_KEY to 302_400L)
        coEvery { api.getCacaoProvider(ADDRESS) } returns cacaoProvider(lastDepositHeight = 1L)

        assertFailsWith<CancellationException> { useCase(ADDRESS) }
    }

    private fun givenChainState(lastDeposit: Long, current: Long, maturity: Long) {
        coEvery { api.getLatestBlock() } returns latestBlock(height = current)
        coEvery { api.getMayaConstants() } returns mapOf(MATURITY_KEY to maturity)
        coEvery { api.getCacaoProvider(ADDRESS) } returns
            cacaoProvider(lastDepositHeight = lastDeposit)
    }

    private fun latestBlock(height: Long): MayaLatestBlockInfoResponse {
        val blockId = BlockIdJson(hash = "", parts = PartsJson(total = 0, hash = ""))
        val header =
            HeaderJson(
                version = VersionJson(block = "0"),
                chainId = "mayachain-mainnet-v1",
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
            cacaoAddress = ADDRESS,
            units = "0",
            value = "0",
            pnl = "0",
            depositAmount = "0",
            withdrawAmount = "0",
            lastDepositHeight = lastDepositHeight,
            lastWithdrawHeight = 0L,
        )

    companion object {
        private const val ADDRESS = "maya1abcdef"
        private const val MATURITY_KEY = "CACAOPOOLDEPOSITMATURITYBLOCKS"
    }
}
