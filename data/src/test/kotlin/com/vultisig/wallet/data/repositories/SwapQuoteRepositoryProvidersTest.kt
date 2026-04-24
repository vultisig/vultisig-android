package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.KyberSwapErrorResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteData
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapEstimateJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteError
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTotalDataJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTransactionJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [SwapQuoteRepositoryImpl] covering all swap providers.
 *
 * These tests verify repository-level logic: error-to-exception mapping and object assembly. API
 * interfaces are mocked, so HTTP transport and JSON deserialization are intentionally not exercised
 * here.
 */
class SwapQuoteRepositoryProvidersTest {

    private val thorChainApi: ThorChainApi = mockk()
    private val mayaChainApi: MayaChainApi = mockk()
    private val oneInchApi: OneInchApi = mockk()
    private val liFiChainApi: LiFiChainApi = mockk()
    private val jupiterApi: JupiterApi = mockk()
    private val kyberApi: KyberApi = mockk()

    private val repository =
        SwapQuoteRepositoryImpl(
            thorChainApi = thorChainApi,
            mayaChainApi = mayaChainApi,
            oneInchApi = oneInchApi,
            liFiChainApi = liFiChainApi,
            jupiterApi = jupiterApi,
            kyberApi = kyberApi,
        )

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String = "",
        isNativeToken: Boolean = contractAddress.isEmpty(),
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "0xaddress",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private val ethCoin = coin(Chain.Ethereum, "ETH")
    private val btcCoin = coin(Chain.Bitcoin, "BTC")
    private val usdcEthCoin =
        coin(Chain.Ethereum, "USDC", contractAddress = "0xusdc", isNativeToken = false)
    private val solCoin = coin(Chain.Solana, "SOL")
    private val usdcSolCoin =
        coin(
            Chain.Solana,
            "USDC",
            contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            isNativeToken = false,
        )

    private fun makeThorQuote(
        expectedAmountOut: String = "9500000000",
        feesTotal: String = "500000000",
        minAmountIn: String = "100000000",
    ) =
        THORChainSwapQuote(
            dustThreshold = null,
            expectedAmountOut = expectedAmountOut,
            expiry = BigInteger.ZERO,
            fees = Fees(affiliate = "0", asset = "0", outbound = "0", total = feesTotal),
            inboundAddress = null,
            inboundConfirmationBlocks = null,
            inboundConfirmationSeconds = null,
            maxStreamingQuantity = 0,
            memo = null,
            notes = "",
            outboundDelayBlocks = BigInteger.ZERO,
            outboundDelaySeconds = BigInteger.ZERO,
            recommendedMinAmountIn = minAmountIn,
            streamingSwapBlocks = BigInteger.ZERO,
            totalSwapSeconds = null,
            warning = "",
            router = null,
            error = null,
        )

    private fun makeEvmQuote() =
        EVMSwapQuoteJson(
            dstAmount = "1000000",
            tx =
                OneInchSwapTxJson(
                    from = "0xfrom",
                    to = "0xto",
                    gas = 200000L,
                    data = "0x",
                    value = "0",
                    gasPrice = "1000000000",
                ),
        )

    private fun makeLiFiQuote() =
        LiFiSwapQuoteJson(
            estimate = LiFiSwapEstimateJson(toAmount = "1000000", feeCosts = emptyList()),
            transactionRequest =
                LiFiSwapTxJson(
                    from = "0xfrom",
                    to = "0xto",
                    gasLimit = "0x30d40",
                    data = "0x",
                    value = "0x0",
                    gasPrice = "0x3b9aca00",
                ),
        )

    @Test
    fun `getSwapQuote returns ThorChain quote on success`() = runTest {
        coEvery {
            thorChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Result(makeThorQuote())

        val result =
            repository.getSwapQuote(
                dstAddress = "btcAddress",
                srcToken = ethCoin,
                dstToken = btcCoin,
                tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
            )

        assertInstanceOf(SwapQuote.ThorChain::class.java, result)
    }

    @Test
    fun `getSwapQuote maps pool-not-found error to SwapRouteNotAvailable`() = runTest {
        coEvery {
            thorChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } throws Exception("Failed to simulate swap: pool does not exist")

        val ex =
            assertThrows<SwapException> {
                repository.getSwapQuote(
                    dstAddress = "btcAddress",
                    srcToken = ethCoin,
                    dstToken = btcCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                )
            }
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, ex)
    }

    @Test
    fun `getMayaSwapQuote returns MayaChain quote on success`() = runTest {
        coEvery {
            mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Result(makeThorQuote())

        val result =
            repository.getMayaSwapQuote(
                dstAddress = "dashAddress",
                srcToken = ethCoin,
                dstToken = btcCoin,
                tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                isAffiliate = true,
            )

        assertInstanceOf(SwapQuote.MayaChain::class.java, result)
    }

    @Test
    fun `getMayaSwapQuote maps outbound-does-not-meet-requirements to InsufficentSwapAmount`() =
        runTest {
            coEvery {
                mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
            } returns
                THORChainSwapQuoteDeserialized.Error(
                    THORChainSwapQuoteError("outbound amount does not meet requirements")
                )

            val ex =
                assertThrows<SwapException> {
                    repository.getMayaSwapQuote(
                        dstAddress = "dashAddress",
                        srcToken = ethCoin,
                        dstToken = btcCoin,
                        tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                        isAffiliate = true,
                    )
                }
            assertInstanceOf(SwapException.InsufficentSwapAmount::class.java, ex)
        }

    @Test
    fun `getOneInchSwapQuote returns EVMSwapQuoteJson on success`() = runTest {
        coEvery { oneInchApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns
            EVMSwapQuoteDeserialized.Result(makeEvmQuote())

        val result =
            repository.getOneInchSwapQuote(
                srcToken = ethCoin,
                dstToken = usdcEthCoin,
                tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                isAffiliate = true,
            )

        assertEquals("1000000", result.dstAmount)
    }

    @Test
    fun `getOneInchSwapQuote maps insufficient-funds error to InsufficientFunds`() = runTest {
        coEvery { oneInchApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns
            EVMSwapQuoteDeserialized.Error("insufficient funds")

        val ex =
            assertThrows<SwapException> {
                repository.getOneInchSwapQuote(
                    srcToken = ethCoin,
                    dstToken = usdcEthCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                    isAffiliate = true,
                )
            }
        assertInstanceOf(SwapException.InsufficientFunds::class.java, ex)
    }

    @Test
    fun `getLiFiSwapQuote returns EVMSwapQuoteJson on success`() = runTest {
        coEvery {
            liFiChainApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any(), any())
        } returns LiFiSwapQuoteDeserialized.Result(makeLiFiQuote())

        val result =
            repository.getLiFiSwapQuote(
                srcAddress = "0xsrc",
                dstAddress = "0xdst",
                srcToken = usdcEthCoin,
                dstToken = ethCoin,
                tokenValue = TokenValue(value = BigInteger("1000000"), token = usdcEthCoin),
                bpsDiscount = 0,
            )

        assertEquals("1000000", result.dstAmount)
    }

    @Test
    fun `getLiFiSwapQuote maps no-available-quotes to SwapRouteNotAvailable`() = runTest {
        coEvery {
            liFiChainApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any(), any())
        } returns
            LiFiSwapQuoteDeserialized.Error(
                LiFiSwapQuoteError("no available quotes for the requested")
            )

        val ex =
            assertThrows<SwapException> {
                repository.getLiFiSwapQuote(
                    srcAddress = "0xsrc",
                    dstAddress = "0xdst",
                    srcToken = usdcEthCoin,
                    dstToken = ethCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000"), token = usdcEthCoin),
                    bpsDiscount = 0,
                )
            }
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, ex)
    }

    @Test
    fun `getKyberSwapQuote maps not-enough-asset error to InsufficentSwapAmount`() = runTest {
        coEvery { kyberApi.getSwapQuote(any(), any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteDeserialized.Error(
                KyberSwapErrorResponse("not enough asset to pay for fees")
            )

        val ex =
            assertThrows<SwapException> {
                repository.getKyberSwapQuote(
                    srcToken = ethCoin,
                    dstToken = usdcEthCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                    affiliateBps = 50,
                )
            }
        assertInstanceOf(SwapException.InsufficentSwapAmount::class.java, ex)
    }

    @Test
    fun `getKyberSwapQuote returns EVMSwapQuoteJson on success`() = runTest {
        coEvery { kyberApi.getSwapQuote(any(), any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteDeserialized.Result(mockk<KyberSwapRouteResponse>(relaxed = true))
        coEvery { kyberApi.getKyberSwapQuote(any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteJson(
                code = 0,
                message = "success",
                data =
                    KyberSwapQuoteData(
                        amountIn = "1000000",
                        amountInUsd = "1.0",
                        amountOut = "990000",
                        amountOutUsd = "0.99",
                        gas = null,
                        gasUsd = "0.01",
                        data = "0x",
                        routerAddress = "0xrouter",
                        transactionValue = "0",
                    ),
                requestId = "req-id",
            )

        val result =
            repository.getKyberSwapQuote(
                srcToken = ethCoin,
                dstToken = usdcEthCoin,
                tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                affiliateBps = 50,
            )

        assertEquals("990000", result.dstAmount)
    }

    @Test
    fun `getKyberSwapQuote maps no-available-quotes error to SwapRouteNotAvailable`() = runTest {
        coEvery { kyberApi.getSwapQuote(any(), any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteDeserialized.Error(
                KyberSwapErrorResponse("no available quotes for the requested token pair")
            )

        val ex =
            assertThrows<SwapException> {
                repository.getKyberSwapQuote(
                    srcToken = ethCoin,
                    dstToken = usdcEthCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000"), token = ethCoin),
                    affiliateBps = 50,
                )
            }
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, ex)
    }

    @Test
    fun `getJupiterSwapQuote returns EVMSwapQuoteJson with correct dst amount`() = runTest {
        val jupiterResult =
            QuoteSwapTotalDataJson(
                swapTransaction = QuoteSwapTransactionJson(data = "base64txdata"),
                dstAmount = "1000000000",
                routePlan = emptyList(),
            )
        coEvery { jupiterApi.getSwapQuote(any(), any(), any(), any()) } returns jupiterResult

        val result =
            repository.getJupiterSwapQuote(
                srcAddress = "solanaAddress",
                srcToken = solCoin,
                dstToken = usdcSolCoin,
                tokenValue = TokenValue(value = BigInteger("1000000000"), token = solCoin),
            )

        assertEquals("1000000000", result.dstAmount)
    }

    @Test
    fun `getJupiterSwapQuote maps rate-limit exception to RateLimitExceeded`() = runTest {
        coEvery { jupiterApi.getSwapQuote(any(), any(), any(), any()) } throws
            SwapException.RateLimitExceeded("[Jupiter] Too many requests")

        val ex =
            assertThrows<SwapException> {
                repository.getJupiterSwapQuote(
                    srcAddress = "solanaAddress",
                    srcToken = solCoin,
                    dstToken = usdcSolCoin,
                    tokenValue = TokenValue(value = BigInteger("1000000000"), token = solCoin),
                )
            }
        assertInstanceOf(SwapException.RateLimitExceeded::class.java, ex)
    }
}
