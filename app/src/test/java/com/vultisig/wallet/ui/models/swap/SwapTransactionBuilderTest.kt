@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SwapTransactionBuilderTest {

    private val swapGasCalculator: SwapGasCalculator = mockk(relaxed = true)
    private val allowanceRepository: AllowanceRepository = mockk(relaxed = true)

    private lateinit var builder: SwapTransactionBuilder

    private val specificAndUtxo =
        BlockChainSpecificAndUtxo(blockChainSpecific = mockk(relaxed = true))

    @BeforeEach
    fun setUp() {
        builder = SwapTransactionBuilder(swapGasCalculator, allowanceRepository)
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(
                srcToken = any(),
                srcAddress = any(),
                gasFee = any(),
                isThorchainRouterDeposit = any(),
                dstAddress = any(),
                memo = any(),
                tokenAmountValue = any(),
            )
        } returns specificAndUtxo
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null
    }

    @Test
    fun `builds ThorChain swap with inbound dst address and estimated-fee fallbacks`() = runTest {
        val srcToken = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8, isNative = true)
        val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
        val data =
            mockk<THORChainSwapQuote>(relaxed = true).apply {
                every { router } returns null
                every { inboundAddress } returns "thor-inbound"
                every { memo } returns "=:ETH.ETH:0xdst"
            }
        val quote =
            SwapQuote.ThorChain(
                expectedDstValue = TokenValue(BigInteger.valueOf(100), dstToken),
                fees = TokenValue(BigInteger.valueOf(7), srcToken),
                expiredAt = Clock.System.now(),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, srcToken),
                data = data,
            )
        val estimatedNetworkFee = TokenValue(BigInteger.valueOf(11), srcToken)
        val estimatedNetworkFeeFiat = FiatValue(BigDecimal("1.50"), "USD")

        val tx =
            builder.build(
                vaultId = "vault-1",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "bc1qsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(5), srcToken),
                gasFeeFiatValue = FiatValue(BigDecimal("0.99"), "USD"),
                estimatedNetworkFeeTokenValue = estimatedNetworkFee,
                estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiat,
            )

        assertEquals("vault-1", tx.vaultId)
        assertEquals(srcToken, tx.srcToken)
        assertEquals(dstToken, tx.dstToken)
        assertEquals("thor-inbound", tx.dstAddress)
        assertEquals(specificAndUtxo, tx.blockChainSpecific)
        // estimatedNetworkFee* take precedence over the passed gasFee/gasFeeFiatValue
        assertEquals(estimatedNetworkFee, tx.gasFees)
        assertEquals(estimatedNetworkFeeFiat, tx.gasFeeFiatValue)
        assertFalse(tx.isApprovalRequired)
        assertIs<SwapPayload.ThorChain>(tx.payload)
    }

    @Test
    fun `builds MayaChain swap with inbound dst address for native source`() = runTest {
        val srcToken = coin(Chain.MayaChain, "CACAO", "maya1src", 10, isNative = true)
        val dstToken = coin(Chain.Bitcoin, "BTC", "bc1qdst", 8, isNative = true)
        val data =
            mockk<THORChainSwapQuote>(relaxed = true).apply {
                every { router } returns null
                every { inboundAddress } returns "maya-inbound"
                every { memo } returns "=:BTC.BTC:bc1qdst"
            }
        val quote =
            SwapQuote.MayaChain(
                expectedDstValue = TokenValue(BigInteger.valueOf(200), dstToken),
                fees = TokenValue(BigInteger.valueOf(3), srcToken),
                expiredAt = Clock.System.now(),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, srcToken),
                data = data,
            )

        val tx =
            builder.build(
                vaultId = "vault-2",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "maya1src",
                srcTokenValue = TokenValue(BigInteger.valueOf(500), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(4), srcToken),
                gasFeeFiatValue = FiatValue(BigDecimal("0.10"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )

        assertEquals("maya-inbound", tx.dstAddress)
        // No estimated fee available -> falls back to the passed gasFee / gasFeeFiatValue
        assertEquals(TokenValue(BigInteger.valueOf(4), srcToken), tx.gasFees)
        assertEquals(FiatValue(BigDecimal("0.10"), "USD"), tx.gasFeeFiatValue)
        assertFalse(tx.isApprovalRequired)
        assertIs<SwapPayload.MayaChain>(tx.payload)
    }

    @Test
    fun `builds SwapKit swap targeting the deposit address and never requires approval`() =
        runTest {
            val srcToken = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8, isNative = true)
            val dstToken = coin(Chain.Ton, "TON", "ton-dst", 9, isNative = true)
            coEvery {
                swapGasCalculator.getSpecificAndUtxo(
                    srcToken = any(),
                    srcAddress = any(),
                    gasFee = any(),
                )
            } returns specificAndUtxo
            val data =
                mockk<SwapKitSwapPayloadJson>(relaxed = true).apply {
                    every { txType } returns SwapKitSwapPayloadJson.TX_TYPE_TON
                    every { targetAddress } returns "swapkit-deposit"
                    every { memo } returns null
                }
            val quote =
                SwapQuote.SwapKit(
                    expectedDstValue = TokenValue(BigInteger.valueOf(300), dstToken),
                    fees = TokenValue(BigInteger.valueOf(2), srcToken),
                    expiredAt = Clock.System.now(),
                    data = data,
                    subProvider = "NEAR",
                )

            val tx =
                builder.build(
                    vaultId = "vault-3",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "bc1qsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(900), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(6), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("0.20"), "USD"),
                    estimatedNetworkFeeTokenValue = null,
                    estimatedNetworkFeeFiatValue = null,
                )

            assertEquals("swapkit-deposit", tx.dstAddress)
            assertFalse(tx.isApprovalRequired)
            assertIs<SwapPayload.SwapKit>(tx.payload)
        }

    @Test
    fun `rejects SwapKit route whose txType has no wired signing path`() = runTest {
        val srcToken = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8, isNative = true)
        val dstToken = coin(Chain.Ton, "TON", "ton-dst", 9, isNative = true)
        val data =
            mockk<SwapKitSwapPayloadJson>(relaxed = true).apply {
                every { txType } returns "UNSUPPORTED"
                every { targetAddress } returns "swapkit-deposit"
            }
        val quote =
            SwapQuote.SwapKit(
                expectedDstValue = TokenValue(BigInteger.valueOf(300), dstToken),
                fees = TokenValue(BigInteger.valueOf(2), srcToken),
                expiredAt = Clock.System.now(),
                data = data,
                subProvider = null,
            )

        assertFailsWith<IllegalArgumentException> {
            builder.build(
                vaultId = "vault-3",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "bc1qsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(900), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(6), srcToken),
                gasFeeFiatValue = FiatValue(BigDecimal("0.20"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )
        }
    }

    @Test
    fun `builds OneInch swap approving the allowance target proxy when allowance is insufficient`() =
        runTest {
            val srcToken =
                coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                specificAndUtxo
            // Allowance below the swap amount -> approval required.
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns
                BigInteger.ZERO
            val tx0 =
                OneInchSwapTxJson(
                    from = "0xsrc",
                    to = "0xrouter",
                    allowanceTarget = "0xproxy",
                    gas = 21_000,
                    data = "0xdata",
                    value = "0",
                    gasPrice = "1",
                )
            val quote =
                SwapQuote.OneInch(
                    expectedDstValue = TokenValue(BigInteger.valueOf(400), dstToken),
                    fees = TokenValue(BigInteger.valueOf(9), dstToken),
                    expiredAt = Clock.System.now(),
                    data = EVMSwapQuoteJson(dstAmount = "400", tx = tx0),
                    provider = "1inch",
                )

            val tx =
                builder.build(
                    vaultId = "vault-4",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(8), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("2.00"), "USD"),
                    estimatedNetworkFeeTokenValue = null,
                    estimatedNetworkFeeFiatValue = FiatValue(BigDecimal("9.99"), "USD"),
                )

            assertEquals("0xrouter", tx.dstAddress)
            // The ERC20 approve spender is the dedicated allowance target, not the swap `to`.
            assertEquals("0xproxy", tx.approveSpender)
            assertTrue(tx.isApprovalRequired)
            // OneInch always uses the passed gasFeeFiatValue (no estimated-fee fallback).
            assertEquals(FiatValue(BigDecimal("2.00"), "USD"), tx.gasFeeFiatValue)
            // gasFees still falls back to gasFee when no estimated network fee is available.
            assertEquals(TokenValue(BigInteger.valueOf(8), srcToken), tx.gasFees)
            assertIs<SwapPayload.EVM>(tx.payload)
        }

    private fun coin(
        chain: Chain,
        ticker: String,
        address: String,
        decimals: Int,
        isNative: Boolean,
        contract: String = "",
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = address,
            decimal = decimals,
            hexPublicKey = "pub",
            priceProviderID = ticker.lowercase(),
            contractAddress = contract,
            isNativeToken = isNative,
        )
}
