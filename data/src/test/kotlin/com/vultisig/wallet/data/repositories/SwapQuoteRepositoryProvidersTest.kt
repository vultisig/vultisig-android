package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTotalDataJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTransactionJson
import com.vultisig.wallet.data.api.models.quotes.RoutePlanItemJson
import com.vultisig.wallet.data.api.models.quotes.SwapInfoJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.swap.JupiterQuoteSource
import com.vultisig.wallet.data.repositories.swap.MayaQuoteSource
import com.vultisig.wallet.data.repositories.swap.OneInchQuoteSource
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SwapQuoteRepositoryProvidersTest {

    private val oneInchApi: OneInchApi = mockk()
    private val mayaChainApi: MayaChainApi = mockk()
    private val jupiterApi: JupiterApi = mockk()

    private val oneInchSource = OneInchQuoteSource(oneInchApi)
    private val mayaSource = MayaQuoteSource(mayaChainApi)
    private val jupiterSource = JupiterQuoteSource(jupiterApi)

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String = "",
        decimal: Int = 8,
        isNativeToken: Boolean = contractAddress.isEmpty(),
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "0xSender",
            decimal = decimal,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    // ---------- OneInch ----------

    private fun oneInchQuote(error: String? = null) =
        EVMSwapQuoteJson(
            dstAmount = "999",
            tx =
                OneInchSwapTxJson(
                    from = "0xfrom",
                    to = "0xto",
                    gas = 21000,
                    data = "0xdata",
                    value = "0",
                    gasPrice = "1",
                ),
            error = error,
        )

    @Test
    fun `oneInch happy path returns quote data`() = runTest {
        val expected = oneInchQuote()
        coEvery { oneInchApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns
            EVMSwapQuoteDeserialized.Result(expected)

        val result =
            oneInchSource.fetch(
                SwapQuoteRequest(
                    srcToken = coin(Chain.Ethereum, "ETH"),
                    dstToken = coin(Chain.Ethereum, "USDC", contractAddress = "0xusdc"),
                    tokenValue =
                        TokenValue(value = BigInteger("1000"), token = coin(Chain.Ethereum, "ETH")),
                    isAffiliate = true,
                )
            ) as SwapQuoteResult.Evm

        assertEquals(expected, result.data)
    }

    @Test
    fun `oneInch deserialization error throws SwapException`() = runTest {
        coEvery { oneInchApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns
            EVMSwapQuoteDeserialized.Error("Insufficient liquidity")

        assertThrows<SwapException> {
            runBlocking {
                oneInchSource.fetch(
                    SwapQuoteRequest(
                        srcToken = coin(Chain.Ethereum, "ETH"),
                        dstToken = coin(Chain.Ethereum, "USDC", contractAddress = "0xusdc"),
                        tokenValue =
                            TokenValue(
                                value = BigInteger("1000"),
                                token = coin(Chain.Ethereum, "ETH"),
                            ),
                    )
                )
            }
        }
    }

    @Test
    fun `oneInch inner result error throws SwapException`() = runTest {
        coEvery { oneInchApi.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns
            EVMSwapQuoteDeserialized.Result(oneInchQuote(error = "Slippage too high"))

        assertThrows<SwapException> {
            runBlocking {
                oneInchSource.fetch(
                    SwapQuoteRequest(
                        srcToken = coin(Chain.Ethereum, "ETH"),
                        dstToken = coin(Chain.Ethereum, "USDC", contractAddress = "0xusdc"),
                        tokenValue =
                            TokenValue(
                                value = BigInteger("1000"),
                                token = coin(Chain.Ethereum, "ETH"),
                            ),
                    )
                )
            }
        }
    }

    // ---------- Maya ----------

    private fun mayaQuote(
        expectedAmountOut: String = "12000000000",
        feesTotal: String = "100000000",
        recommendedMinAmountIn: String = "100000",
        error: String? = null,
    ) =
        THORChainSwapQuote(
            dustThreshold = null,
            expectedAmountOut = expectedAmountOut,
            expiry = BigInteger.valueOf(9999999),
            fees = Fees(affiliate = "0", asset = feesTotal, outbound = "0", total = feesTotal),
            inboundAddress = "mayaInbound",
            inboundConfirmationBlocks = null,
            inboundConfirmationSeconds = null,
            maxStreamingQuantity = 1,
            memo = "=:ETH.ETH:0xDest",
            notes = "",
            outboundDelayBlocks = BigInteger.ZERO,
            outboundDelaySeconds = BigInteger.ZERO,
            recommendedMinAmountIn = recommendedMinAmountIn,
            streamingSwapBlocks = BigInteger.ZERO,
            totalSwapSeconds = null,
            warning = "",
            router = null,
            error = error,
        )

    @Test
    fun `maya happy path returns SwapQuote MayaChain with mapped fields`() = runTest {
        val quote = mayaQuote(expectedAmountOut = "12000000000", feesTotal = "100000000")
        coEvery {
            mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Result(quote)

        val srcToken = coin(Chain.Bitcoin, "BTC", decimal = 8)
        val dstToken = coin(Chain.Ethereum, "ETH", decimal = 18)
        val result =
            (mayaSource.fetch(
                    SwapQuoteRequest(
                        srcToken = srcToken,
                        dstToken = dstToken,
                        tokenValue = TokenValue(value = BigInteger("100000000"), token = srcToken),
                        dstAddress = "0xDest",
                        isAffiliate = true,
                    )
                ) as SwapQuoteResult.Native)
                .quote as SwapQuote.MayaChain

        // Maya returns amounts in 1e8 (thorswapMultiplier). Converting back to 18-dp ETH:
        // 12000000000 / 1e8 = 120 ETH = 120 * 1e18 = 120000000000000000000
        assertEquals(BigInteger("120000000000000000000"), result.expectedDstValue.value)
        // fees: 100000000 / 1e8 = 1 ETH = 1e18
        assertEquals(BigInteger("1000000000000000000"), result.fees.value)
        // recommendedMinTokenValue uses srcToken (8 decimals): 100000 / 1e8 = 0.001 BTC = 100000
        assertEquals(BigInteger("100000"), result.recommendedMinTokenValue.value)
        assertEquals(quote, result.data)
    }

    @Test
    fun `maya source MayaChain keeps original tokenValue as recommendedMin`() = runTest {
        val quote = mayaQuote(recommendedMinAmountIn = "999999")
        coEvery {
            mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Result(quote)

        val srcToken = coin(Chain.MayaChain, "CACAO", decimal = 10)
        val dstToken = coin(Chain.Ethereum, "ETH", decimal = 18)
        val tokenValue = TokenValue(value = BigInteger("777"), token = srcToken)

        val result =
            (mayaSource.fetch(
                    SwapQuoteRequest(
                        srcToken = srcToken,
                        dstToken = dstToken,
                        tokenValue = tokenValue,
                        dstAddress = "0xDest",
                    )
                ) as SwapQuoteResult.Native)
                .quote as SwapQuote.MayaChain

        // For MayaChain source the original tokenValue is returned untouched
        assertEquals(tokenValue, result.recommendedMinTokenValue)
    }

    @Test
    fun `maya deserialization error throws SwapException`() = runTest {
        coEvery {
            mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Error(THORChainSwapQuoteError("pool unavailable"))

        val srcToken = coin(Chain.Bitcoin, "BTC")
        val dstToken = coin(Chain.Ethereum, "ETH", decimal = 18)
        assertThrows<SwapException> {
            runBlocking {
                mayaSource.fetch(
                    SwapQuoteRequest(
                        srcToken = srcToken,
                        dstToken = dstToken,
                        tokenValue = TokenValue(value = BigInteger("100000000"), token = srcToken),
                        dstAddress = "0xDest",
                    )
                )
            }
        }
    }

    @Test
    fun `maya inner quote error throws SwapException`() = runTest {
        coEvery {
            mayaChainApi.getSwapQuotes(any(), any(), any(), any(), any(), any(), any())
        } returns THORChainSwapQuoteDeserialized.Result(mayaQuote(error = "trading halted"))

        val srcToken = coin(Chain.Bitcoin, "BTC")
        val dstToken = coin(Chain.Ethereum, "ETH", decimal = 18)
        assertThrows<SwapException> {
            runBlocking {
                mayaSource.fetch(
                    SwapQuoteRequest(
                        srcToken = srcToken,
                        dstToken = dstToken,
                        tokenValue = TokenValue(value = BigInteger("100000000"), token = srcToken),
                        dstAddress = "0xDest",
                    )
                )
            }
        }
    }

    // ---------- Jupiter ----------

    private fun jupiterQuoteResponse(
        dstAmount: String = "1500000",
        feeMint: String = SOL_MINT,
        feeAmount: String = "1234",
    ) =
        QuoteSwapTotalDataJson(
            swapTransaction = QuoteSwapTransactionJson(data = "AQID"),
            dstAmount = dstAmount,
            routePlan =
                listOf(
                    RoutePlanItemJson(
                        swapInfo =
                            SwapInfoJson(
                                ammKey = "amm1",
                                label = "Orca",
                                inputMint = SOL_MINT,
                                outputMint = "outputMint",
                                inAmount = "1000",
                                outAmount = "1500000",
                                feeAmount = feeAmount,
                                feeMint = feeMint,
                            ),
                        percent = 100,
                    )
                ),
        )

    @Test
    fun `jupiter happy path with matching feeMint returns swap fee from route`() = runTest {
        coEvery { jupiterApi.getSwapQuote(any(), any(), any(), any()) } returns
            jupiterQuoteResponse(feeMint = SOL_MINT, feeAmount = "1234")

        // Native SOL → empty contract address triggers SOL default mint mapping
        val sol = coin(Chain.Solana, "SOL", contractAddress = "")
        val usdc = coin(Chain.Solana, "USDC", contractAddress = USDC_MINT)
        val result =
            (jupiterSource.fetch(
                    SwapQuoteRequest(
                        srcToken = sol,
                        dstToken = usdc,
                        tokenValue = TokenValue(value = BigInteger("1000"), token = sol),
                        srcAddress = "WALLET",
                    )
                ) as SwapQuoteResult.Evm)
                .data

        assertEquals("1500000", result.dstAmount)
        assertEquals("1234", result.tx.swapFee)
        assertEquals(SOL_MINT, result.tx.swapFeeTokenContract)
        assertEquals("AQID", result.tx.data)
    }

    @Test
    fun `jupiter no matching feeMint returns zero swap fee`() = runTest {
        coEvery { jupiterApi.getSwapQuote(any(), any(), any(), any()) } returns
            jupiterQuoteResponse(feeMint = "differentMint", feeAmount = "9999")

        val sol = coin(Chain.Solana, "SOL", contractAddress = "")
        val usdc = coin(Chain.Solana, "USDC", contractAddress = USDC_MINT)
        val result =
            (jupiterSource.fetch(
                    SwapQuoteRequest(
                        srcToken = sol,
                        dstToken = usdc,
                        tokenValue = TokenValue(value = BigInteger("1000"), token = sol),
                        srcAddress = "WALLET",
                    )
                ) as SwapQuoteResult.Evm)
                .data

        assertEquals("0", result.tx.swapFee)
        // swapFeeTokenContract still falls back to first route's feeMint
        assertEquals("differentMint", result.tx.swapFeeTokenContract)
    }

    @Test
    fun `jupiter api throws is wrapped in SwapException`() = runTest {
        coEvery { jupiterApi.getSwapQuote(any(), any(), any(), any()) } throws
            RuntimeException("rate limited")

        val sol = coin(Chain.Solana, "SOL", contractAddress = "")
        val usdc = coin(Chain.Solana, "USDC", contractAddress = USDC_MINT)
        assertThrows<SwapException> {
            runBlocking {
                jupiterSource.fetch(
                    SwapQuoteRequest(
                        srcToken = sol,
                        dstToken = usdc,
                        tokenValue = TokenValue(value = BigInteger("1000"), token = sol),
                        srcAddress = "WALLET",
                    )
                )
            }
        }
    }

    companion object {
        const val SOL_MINT = "So11111111111111111111111111111111111111112"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    }
}
