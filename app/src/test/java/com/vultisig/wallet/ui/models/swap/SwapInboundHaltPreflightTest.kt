package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.payload.SwapPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class SwapInboundHaltPreflightTest {

    private val thorChainApi = mockk<ThorChainApi>()
    private val mayaChainApi = mockk<MayaChainApi>()
    private val preflight = SwapInboundHaltPreflight(thorChainApi, mayaChainApi)

    @Test
    fun `THORChain route is blocked when its source chain inbound is halted`() = runTest {
        val transaction = transaction(SwapPayload.ThorChain(mockk(relaxed = true)), Chain.Bitcoin)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "BTC", halted = true))

        assertFailsWith<SwapException.TradingHalted> {
            preflight.assertSourceChainNotHalted(transaction)
        }
        coVerify(exactly = 0) { mayaChainApi.getInboundAddresses() }
    }

    @Test
    fun `MayaChain route is blocked when global trading is paused`() = runTest {
        val transaction = transaction(SwapPayload.MayaChain(mockk(relaxed = true)), Chain.Ethereum)
        coEvery { mayaChainApi.getInboundAddresses() } returns
            listOf(inbound(chain = "eth", globalTradingPaused = true))

        assertFailsWith<SwapException.TradingHalted> {
            preflight.assertSourceChainNotHalted(transaction)
        }
        coVerify(exactly = 0) { thorChainApi.getTHORChainInboundAddresses() }
    }

    @Test
    fun `native route proceeds when source chain inbound is active`() = runTest {
        val transaction = transaction(SwapPayload.ThorChain(mockk(relaxed = true)), Chain.Bitcoin)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "BTC"))

        preflight.assertSourceChainNotHalted(transaction)
    }

    @Test
    fun `native route proceeds when source chain is absent from inbound response`() = runTest {
        val transaction = transaction(SwapPayload.ThorChain(mockk(relaxed = true)), Chain.Bitcoin)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "ETH", halted = true))

        // Matches the iOS gate: a missing entry is not treated as a confirmed source-chain halt.
        preflight.assertSourceChainNotHalted(transaction)
    }

    @Test
    fun `native route fails closed when inbound fetch fails`() = runTest {
        val transaction = transaction(SwapPayload.MayaChain(mockk(relaxed = true)), Chain.Bitcoin)
        coEvery { mayaChainApi.getInboundAddresses() } throws IllegalStateException("offline")

        assertFailsWith<SwapException.TradingHalted> {
            preflight.assertSourceChainNotHalted(transaction)
        }
    }

    @Test
    fun `non-native provider does not fetch inbound status`() = runTest {
        val transaction = transaction(mockk<SwapPayload.EVM>(relaxed = true), Chain.Ethereum)

        preflight.assertSourceChainNotHalted(transaction)

        coVerify(exactly = 0) { thorChainApi.getTHORChainInboundAddresses() }
        coVerify(exactly = 0) { mayaChainApi.getInboundAddresses() }
    }

    private fun transaction(swapPayload: SwapPayload, sourceChain: Chain): SwapTransaction =
        mockk(relaxed = true) {
            every { payload } returns swapPayload
            every { srcToken } returns
                mockk<Coin>(relaxed = true) { every { chain } returns sourceChain }
        }

    private fun inbound(
        chain: String,
        halted: Boolean = false,
        globalTradingPaused: Boolean = false,
        chainTradingPaused: Boolean = false,
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = "inbound-address",
            halted = halted,
            globalTradingPaused = globalTradingPaused,
            chainTradingPaused = chainTradingPaused,
            chainLPActionsPaused = false,
            gasRate = "1",
            gasRateUnits = "satsperbyte",
        )
}
