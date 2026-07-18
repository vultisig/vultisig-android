@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import io.mockk.coEvery
import io.mockk.coVerify
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
                every { fees } returns
                    Fees(affiliate = "0", asset = "0", outbound = "0", total = "0")
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
        val payload = assertIs<SwapPayload.ThorChain>(tx.payload)
        // ThorChain streams in single-block intervals; MayaChain uses "3".
        assertEquals("1", payload.data.streamingInterval)
        assertEquals("0", payload.data.toAmountLimit)
        assertTrue(payload.data.isAffiliate)
        // Expiration is stamped ~15 minutes ahead, so it must be in the future.
        assertTrue(payload.data.expirationTime > (System.currentTimeMillis() / 1000).toULong())
    }

    @Test
    fun `builds MayaChain swap with inbound dst address for native source`() = runTest {
        val srcToken = coin(Chain.MayaChain, "CACAO", "maya1src", 10, isNative = true)
        val dstToken = coin(Chain.Bitcoin, "BTC", "bc1qdst", 8, isNative = true)
        val data =
            mockk<THORChainSwapQuote>(relaxed = true).apply {
                every { fees } returns
                    Fees(affiliate = "0", asset = "0", outbound = "0", total = "0")
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
        val payload = assertIs<SwapPayload.MayaChain>(tx.payload)
        // MayaChain streams in 3-block intervals; ThorChain uses "1".
        assertEquals("3", payload.data.streamingInterval)
        assertEquals("0", payload.data.toAmountLimit)
        assertTrue(payload.data.isAffiliate)
        // Expiration is stamped ~15 minutes ahead, so it must be in the future.
        assertTrue(payload.data.expirationTime > (System.currentTimeMillis() / 1000).toULong())
    }

    @Test
    fun `builds ThorChain swap routing an EVM token deposit through the router`() = runTest {
        val srcToken =
            coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Bitcoin, "BTC", "bc1qdst", 8, isNative = true)
        val data =
            mockk<THORChainSwapQuote>(relaxed = true).apply {
                every { fees } returns
                    Fees(affiliate = "0", asset = "0", outbound = "0", total = "0")
                every { router } returns "0xrouter"
                every { inboundAddress } returns "thor-inbound"
                every { memo } returns "=:BTC.BTC:bc1qdst"
            }
        val quote =
            SwapQuote.ThorChain(
                expectedDstValue = TokenValue(BigInteger.valueOf(100), dstToken),
                fees = TokenValue(BigInteger.valueOf(7), srcToken),
                expiredAt = Clock.System.now(),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, srcToken),
                data = data,
            )
        val srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken)
        val gasFee = TokenValue(BigInteger.valueOf(5), srcToken)

        val tx =
            builder.build(
                vaultId = "vault-1",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = srcTokenValue,
                quote = quote,
                gasFee = gasFee,
                gasFeeFiatValue = FiatValue(BigDecimal("0.99"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )

        // router takes precedence over inboundAddress for the dst address.
        assertEquals("0xrouter", tx.dstAddress)
        // EVM non-native source is a router deposit, so dstAddress/memo/amount are forwarded.
        coVerify {
            swapGasCalculator.getSpecificAndUtxo(
                srcToken = srcToken,
                srcAddress = "0xsrc",
                gasFee = gasFee,
                isThorchainRouterDeposit = true,
                dstAddress = "0xrouter",
                memo = "=:BTC.BTC:bc1qdst",
                tokenAmountValue = srcTokenValue.value,
            )
        }
    }

    @Test
    fun `builds MayaChain swap resolving the EVM source dst address from the router`() = runTest {
        val srcToken =
            coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Bitcoin, "BTC", "bc1qdst", 8, isNative = true)
        val data =
            mockk<THORChainSwapQuote>(relaxed = true).apply {
                every { fees } returns
                    Fees(affiliate = "0", asset = "0", outbound = "0", total = "0")
                every { router } returns "0xrouter"
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
        val srcTokenValue = TokenValue(BigInteger.valueOf(500), srcToken)
        val gasFee = TokenValue(BigInteger.valueOf(4), srcToken)

        val tx =
            builder.build(
                vaultId = "vault-2",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = srcTokenValue,
                quote = quote,
                gasFee = gasFee,
                gasFeeFiatValue = FiatValue(BigDecimal("0.10"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )

        // EVM source resolves the router before the inbound address.
        assertEquals("0xrouter", tx.dstAddress)
        coVerify {
            swapGasCalculator.getSpecificAndUtxo(
                srcToken = srcToken,
                srcAddress = "0xsrc",
                gasFee = gasFee,
                isThorchainRouterDeposit = true,
                dstAddress = "0xrouter",
                memo = "=:BTC.BTC:bc1qdst",
                tokenAmountValue = srcTokenValue.value,
            )
        }
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
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(99))
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
            // Without an override the displayed EVM network fee comes from the payload's signed gas
            // params (maxFeePerGasWei 99 × the 600k display limit), the same value the joined
            // device
            // derives, re-priced from the gas-pass pair: 2.00 × 59_400_000 / 8 = 14_850_000
            // (#5329).
            assertEquals(BigInteger.valueOf(59_400_000), tx.gasFees.value)
            assertEquals("USD", tx.gasFeeFiatValue.currency)
            assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("14850000")))
            val payload = assertIs<SwapPayload.EVM>(tx.payload)
            // On an Ethereum plan the tx gas price is patched with the plan's maxFeePerGas.
            assertEquals("99", payload.data.quote.tx.gasPrice)
            // Non-Mantle chains keep the quote's original gas limit.
            assertEquals(21_000L, payload.data.quote.tx.gas)
        }

    @Test
    fun `values the OP-stack swap network fee at the joiner-aligned limit for cross-device parity`() =
        runTest {
            val srcToken = coin(Chain.Base, "ETH", "0xsrc", 18, isNative = true)
            val dstToken =
                coin(Chain.Base, "USDC", "0xdst", 6, isNative = false, contract = "0xtoken")
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(100))
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
                    vaultId = "vault-op",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(1_000_000), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("1.00"), "USD"),
                    // An OP-stack estimate folds in an L1 data fee the joiner has no term for, so
                    // keeping it here would diverge from the joined device (#5329).
                    estimatedNetworkFeeTokenValue =
                        TokenValue(BigInteger.valueOf(5_000_000), srcToken),
                    estimatedNetworkFeeFiatValue = FiatValue(BigDecimal("5.00"), "USD"),
                )

            // evmSwapDisplayGasLimit returns null for OP-stack L2s → DEFAULT_SWAP_LIMIT (600k), the
            // same limit computeJoinKeysignSwapNetworkFee uses, so both devices show 100 × 600_000
            // =
            // 60_000_000 wei (not the L1-inclusive 5_000_000 estimate). Fiat re-prices off the
            // estimate pair: 5.00 × 60_000_000 / 5_000_000 = 60.00.
            assertEquals(BigInteger.valueOf(60_000_000), tx.gasFees.value)
            assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("60.00")))
        }

    @Test
    fun `values the OneInch swap fee off the stamped gas params for cross-device parity`() =
        runTest {
            val srcToken =
                coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(100))
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
                    // 1inch's original quoted gasPrice × gas, stamped by the manager as the
                    // "Swap Fee" placeholder; the initiator overwrites tx.gasPrice/gas below.
                    expectedDstValue = TokenValue(BigInteger.valueOf(400), dstToken),
                    fees = TokenValue(BigInteger.valueOf(21_000), srcToken),
                    expiredAt = Clock.System.now(),
                    data = EVMSwapQuoteJson(dstAmount = "400", tx = tx0),
                    provider = "1inch",
                )

            val tx =
                builder.build(
                    vaultId = "vault-swap-fee-parity",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(1_000_000), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("1.00"), "USD"),
                    estimatedNetworkFeeTokenValue = null,
                    estimatedNetworkFeeFiatValue = null,
                )

            // The joiner re-derives the Swap Fee from the signed tx's gasPrice × gas
            // (maxFeePerGasWei 100 × the stamped 21_000 gas limit = 2_100_000), so the initiator
            // must value its Swap Fee off the same stamped params rather than 1inch's original
            // 21_000 placeholder (#5329).
            val payload = assertIs<SwapPayload.EVM>(tx.payload)
            assertEquals("100", payload.data.quote.tx.gasPrice)
            assertEquals(21_000L, payload.data.quote.tx.gas)
            assertEquals(BigInteger.valueOf(2_100_000), tx.estimatedFees.value)
        }

    @Test
    fun `builds OneInch swap keeping the quote gas limit on Mantle`() = runTest {
        val srcToken =
            coin(Chain.Mantle, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Mantle, "MNT", "0xdst", 18, isNative = true)
        coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
            ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(7))
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
                vaultId = "vault-5",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(8), srcToken),
                gasFeeFiatValue = FiatValue(BigDecimal("2.00"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )

        val payload = assertIs<SwapPayload.EVM>(tx.payload)
        // Mantle now keeps the quote's original gas limit like every other EVM chain.
        assertEquals(21_000L, payload.data.quote.tx.gas)
        // The gas price is still patched from the EVM plan.
        assertEquals("7", payload.data.quote.tx.gasPrice)
    }

    @Test
    fun `builds OneInch swap falling back to default gas unit when quote gas is zero`() = runTest {
        val srcToken =
            coin(Chain.Mantle, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Mantle, "MNT", "0xdst", 18, isNative = true)
        coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
            ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(7))
        val tx0 =
            OneInchSwapTxJson(
                from = "0xsrc",
                to = "0xrouter",
                allowanceTarget = "0xproxy",
                gas = 0,
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
                vaultId = "vault-5",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(8), srcToken),
                gasFeeFiatValue = FiatValue(BigDecimal("2.00"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
            )

        val payload = assertIs<SwapPayload.EVM>(tx.payload)
        // A zero-gas quote must never reach the signed payload — fall back to the standard unit.
        assertEquals(EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT, payload.data.quote.tx.gas)
    }

    @Test
    fun `builds OneInch swap applying the gas-limit override to tx gas and the eth specific`() =
        runTest {
            val srcToken =
                coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(7))
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
                    vaultId = "vault-6",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(8), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("2.00"), "USD"),
                    estimatedNetworkFeeTokenValue = null,
                    estimatedNetworkFeeFiatValue = null,
                    gasLimitOverride = 500_000L,
                )

            // OneInchSwap signs with maxOf(tx.gas, ethSpecific.gasLimit), so the override must land
            // on BOTH to be effective whether it raises or lowers the estimate (#4858).
            val payload = assertIs<SwapPayload.EVM>(tx.payload)
            assertEquals(500_000L, payload.data.quote.tx.gas)
            val specific =
                assertIs<BlockChainSpecific.Ethereum>(tx.blockChainSpecific.blockChainSpecific)
            assertEquals(BigInteger.valueOf(500_000), specific.gasLimit)
        }

    @Test
    fun `recomputes the displayed network fee from the gas-limit override`() = runTest {
        val srcToken =
            coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
        val nativeEth = coin(Chain.Ethereum, "ETH", "0xsrc", 18, isNative = true)
        coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
            ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(10))
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
                vaultId = "vault-fee",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(1_000_000), nativeEth),
                gasFeeFiatValue = FiatValue(BigDecimal("3.00"), "USD"),
                estimatedNetworkFeeTokenValue = null,
                estimatedNetworkFeeFiatValue = null,
                gasLimitOverride = 300_000L,
            )

        // A sub-floor override is floored to the 600k ERC-20 display limit — the same value the
        // joiner derives from the stamped tx.gas — so both devices agree: 600_000 × 10 = 6_000_000
        // wei, re-valued via the baseline (gasFeeFiatValue 3.00 ÷ gasFee 1_000_000) → 18.00.
        assertEquals(BigInteger.valueOf(6_000_000), tx.gasFees.value)
        assertEquals("USD", tx.gasFeeFiatValue.currency)
        assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("18.00")))
    }

    @Test
    fun `gas-limit override re-values fiat from the route-gas estimate, not the flat baseline`() =
        runTest {
            val srcToken =
                coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            val nativeEth = coin(Chain.Ethereum, "ETH", "0xsrc", 18, isNative = true)
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(10))
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
                    vaultId = "vault-fee-pair",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    // gasFee stays the flat-600k baseline; the route-gas estimate (#5056) is the
                    // matched display pair the override fiat must re-value against.
                    gasFee = TokenValue(BigInteger.valueOf(6_000_000), nativeEth),
                    gasFeeFiatValue = FiatValue(BigDecimal("4.00"), "USD"),
                    estimatedNetworkFeeTokenValue =
                        TokenValue(BigInteger.valueOf(2_000_000), nativeEth),
                    estimatedNetworkFeeFiatValue = FiatValue(BigDecimal("4.00"), "USD"),
                    gasLimitOverride = 300_000L,
                )

            // The sub-floor override floors to the 600k display limit (joiner-aligned): 600_000 ×
            // 10 = 6_000_000 wei, re-valued via the matched route-gas pair (4.00 ÷ 2_000_000):
            // 4.00 × 6_000_000 / 2_000_000 = 12.00. The unmatched 6M baseline would wrongly give
            // 4.00 × 6_000_000 / 6_000_000 = 4.00.
            assertEquals(BigInteger.valueOf(6_000_000), tx.gasFees.value)
            assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("12.00")))
        }

    @Test
    fun `gas-limit override still applies when the estimate token is a non-null zero`() = runTest {
        val srcToken =
            coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
        val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
        val nativeEth = coin(Chain.Ethereum, "ETH", "0xsrc", 18, isNative = true)
        coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
            ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(10))
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
                vaultId = "vault-zero-estimate",
                srcToken = srcToken,
                dstToken = dstToken,
                srcAddress = "0xsrc",
                srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                quote = quote,
                gasFee = TokenValue(BigInteger.valueOf(1_000_000), nativeEth),
                gasFeeFiatValue = FiatValue(BigDecimal("3.00"), "USD"),
                // A non-null zero estimate must be ignored, not suppress the override.
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger.ZERO, nativeEth),
                estimatedNetworkFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
                gasLimitOverride = 300_000L,
            )

        // The floored override (600k) re-values via the positive gasFee pair, not the zero
        // estimate: 600_000 × 10 = 6_000_000 wei; 3.00 × 6_000_000 / 1_000_000 = 18.00.
        assertEquals(BigInteger.valueOf(6_000_000), tx.gasFees.value)
        assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("18.00")))
    }

    @Test
    fun `falls back to the gas-pass pair when the estimate token is positive but its fiat is null`() =
        runTest {
            val srcToken =
                coin(Chain.Ethereum, "USDC", "0xsrc", 6, isNative = false, contract = "0xtoken")
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            val nativeEth = coin(Chain.Ethereum, "ETH", "0xsrc", 18, isNative = true)
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                ethereumSpecificAndUtxo(maxFeePerGasWei = BigInteger.valueOf(10))
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
                    vaultId = "vault-null-fiat-estimate",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "0xsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(1_000_000), nativeEth),
                    gasFeeFiatValue = FiatValue(BigDecimal("3.00"), "USD"),
                    // Token estimate is positive but its fiat estimate is null: the two must be
                    // used as an atomic pair, so the re-price falls back to the gas-pass pair
                    // instead of dividing gas-pass fiat by the unrelated estimate token fee.
                    estimatedNetworkFeeTokenValue =
                        TokenValue(BigInteger.valueOf(2_000_000), nativeEth),
                    estimatedNetworkFeeFiatValue = null,
                    gasLimitOverride = null,
                )

            // Display limit floors to 600k: 600_000 × 10 = 6_000_000 wei; re-valued via the
            // gas-pass pair (3.00 ÷ 1_000_000): 3.00 × 6_000_000 / 1_000_000 = 18.00. The unmatched
            // estimate pair would wrongly give 3.00 × 6_000_000 / 2_000_000 = 9.00.
            assertEquals(BigInteger.valueOf(6_000_000), tx.gasFees.value)
            assertEquals(0, tx.gasFeeFiatValue.value.compareTo(BigDecimal("18.00")))
        }

    @Test
    fun `stamps the external recipient on the built transaction for verify-screen surfacing`() =
        runTest {
            val srcToken = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8, isNative = true)
            val dstToken = coin(Chain.Ethereum, "ETH", "0xdst", 18, isNative = true)
            val data =
                mockk<THORChainSwapQuote>(relaxed = true).apply {
                    every { fees } returns
                        Fees(affiliate = "0", asset = "0", outbound = "0", total = "0")
                    every { router } returns null
                    every { inboundAddress } returns "thor-inbound"
                    every { memo } returns "=:ETH.ETH:0xExternal"
                }
            val quote =
                SwapQuote.ThorChain(
                    expectedDstValue = TokenValue(BigInteger.valueOf(100), dstToken),
                    fees = TokenValue(BigInteger.valueOf(7), srcToken),
                    expiredAt = Clock.System.now(),
                    recommendedMinTokenValue = TokenValue(BigInteger.ZERO, srcToken),
                    data = data,
                )

            val tx =
                builder.build(
                    vaultId = "vault-7",
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcAddress = "bc1qsrc",
                    srcTokenValue = TokenValue(BigInteger.valueOf(1_000), srcToken),
                    quote = quote,
                    gasFee = TokenValue(BigInteger.valueOf(5), srcToken),
                    gasFeeFiatValue = FiatValue(BigDecimal("0.99"), "USD"),
                    estimatedNetworkFeeTokenValue = null,
                    estimatedNetworkFeeFiatValue = null,
                    externalRecipient = "0xExternalRecipient",
                )

            assertEquals("0xExternalRecipient", tx.externalRecipient)
        }

    /** Plan fixture whose [BlockChainSpecific] is a real [BlockChainSpecific.Ethereum]. */
    private fun ethereumSpecificAndUtxo(maxFeePerGasWei: BigInteger) =
        BlockChainSpecificAndUtxo(
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = maxFeePerGasWei,
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(21_000),
                )
        )

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
