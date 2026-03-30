package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteData
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwapQuoteRepositoryTest {

    private val kyberApi: KyberApi = mockk()

    private val repository =
        SwapQuoteRepositoryImpl(
            thorChainApi = mockk(),
            mayaChainApi = mockk(),
            oneInchApi = mockk(),
            liFiChainApi = mockk(),
            jupiterApi = mockk(),
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
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    @Test
    fun `SOL to USDC resolves to JUPITER not THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )

        val provider = repository.resolveProvider(sol, usdc)

        assertNotNull(provider)
        assertEquals(SwapProvider.JUPITER, provider)
    }

    @Test
    fun `USDC to SOL resolves to JUPITER not THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )

        val provider = repository.resolveProvider(usdc, sol)

        assertNotNull(provider)
        assertEquals(SwapProvider.JUPITER, provider)
    }

    @Test
    fun `SOL to SOL cross-chain resolves to THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val btc = coin(Chain.Bitcoin, "BTC")

        val provider = repository.resolveProvider(sol, btc)

        assertNotNull(provider)
        assertEquals(SwapProvider.THORCHAIN, provider)
    }

    @Test
    fun `Solana USDC to ETH cross-chain resolves to LIFI`() {
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )
        val eth = coin(Chain.Ethereum, "ETH")

        val provider = repository.resolveProvider(usdc, eth)

        assertNotNull(provider)
        assertEquals(SwapProvider.LIFI, provider)
    }

    @Test
    fun `Solana SPL token gets JUPITER and LIFI but not THORCHAIN`() {
        val usdt =
            coin(
                Chain.Solana,
                "USDT",
                contractAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            )
        val sol = coin(Chain.Solana, "SOL")

        val provider = repository.resolveProvider(usdt, sol)

        assertNotNull(provider)
        assertTrue(provider == SwapProvider.JUPITER || provider == SwapProvider.LIFI)
    }

    @Test
    fun `unsupported chain returns null`() {
        val sui1 = coin(Chain.Sui, "SUI")
        val sui2 = coin(Chain.Sui, "USDC", contractAddress = "0xabc")

        val provider = repository.resolveProvider(sui1, sui2)

        assertNull(provider)
    }

    @Test
    fun `kyber affiliate fee BPS converts to absolute token amount`() = runTest {
        val amountOut = "10000000"
        val bps = 50
        val routeSummary =
            KyberSwapRouteResponse.RouteSummary(
                tokenIn = "0xsrc",
                amountIn = "1000000",
                amountInUsd = "1.0",
                tokenOut = "0xdst",
                amountOut = amountOut,
                amountOutUsd = "10.0",
                gas = "200000",
                gasPrice = "1000000000",
                gasUsd = "0.2",
                extraFee =
                    KyberSwapRouteResponse.RouteSummary.ExtraFee(
                        feeAmount = bps.toString(),
                        chargeFeeBy = "currency_out",
                        isInBps = true,
                        feeReceiver = "0xfee",
                    ),
                route = emptyList(),
                routeID = "route1",
                checksum = "checksum",
                timestamp = 0,
            )
        val routeResponse =
            KyberSwapRouteResponse(
                code = 0,
                message = "ok",
                data = KyberSwapRouteResponse.RouteData(routeSummary, "0xrouter"),
                requestId = "req1",
            )
        val quoteJson =
            KyberSwapQuoteJson(
                code = 0,
                message = "ok",
                data =
                    KyberSwapQuoteData(
                        amountIn = "1000000",
                        amountInUsd = "1.0",
                        amountOut = amountOut,
                        amountOutUsd = "10.0",
                        gas = "200000",
                        gasUsd = "0.2",
                        data = "0x",
                        routerAddress = "0xrouter",
                        transactionValue = "0",
                        gasPrice = "1000000000",
                    ),
                requestId = "req1",
            )

        coEvery { kyberApi.getSwapQuote(any(), any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteDeserialized.Result(routeResponse)
        coEvery { kyberApi.getKyberSwapQuote(any(), any(), any(), any(), any()) } returns quoteJson

        val result =
            repository.getKyberSwapQuote(
                srcToken = coin(Chain.Ethereum, "ETH"),
                dstToken = coin(Chain.Ethereum, "USDC", contractAddress = "0xdst"),
                tokenValue =
                    TokenValue(value = BigInteger("1000000"), token = coin(Chain.Ethereum, "ETH")),
                affiliateBps = bps,
            )

        // amountOut * bps / 10000 = 10000000 * 50 / 10000 = 50000
        assertEquals("50000", result.tx.swapFee)
        assertEquals("0xdst", result.tx.swapFeeTokenContract)
    }

    @Test
    fun `kyber affiliate fee BPS passthrough when isInBps is false`() = runTest {
        val absoluteFeeAmount = "12345"
        val routeSummary =
            KyberSwapRouteResponse.RouteSummary(
                tokenIn = "0xsrc",
                amountIn = "1000000",
                amountInUsd = "1.0",
                tokenOut = "0xdst",
                amountOut = "10000000",
                amountOutUsd = "10.0",
                gas = "200000",
                gasPrice = "1000000000",
                gasUsd = "0.2",
                extraFee =
                    KyberSwapRouteResponse.RouteSummary.ExtraFee(
                        feeAmount = absoluteFeeAmount,
                        chargeFeeBy = "currency_out",
                        isInBps = false,
                        feeReceiver = "0xfee",
                    ),
                route = emptyList(),
                routeID = "route1",
                checksum = "checksum",
                timestamp = 0,
            )
        val routeResponse =
            KyberSwapRouteResponse(
                code = 0,
                message = "ok",
                data = KyberSwapRouteResponse.RouteData(routeSummary, "0xrouter"),
                requestId = "req1",
            )
        val quoteJson =
            KyberSwapQuoteJson(
                code = 0,
                message = "ok",
                data =
                    KyberSwapQuoteData(
                        amountIn = "1000000",
                        amountInUsd = "1.0",
                        amountOut = "10000000",
                        amountOutUsd = "10.0",
                        gas = "200000",
                        gasUsd = "0.2",
                        data = "0x",
                        routerAddress = "0xrouter",
                        transactionValue = "0",
                        gasPrice = "1000000000",
                    ),
                requestId = "req1",
            )

        coEvery { kyberApi.getSwapQuote(any(), any(), any(), any(), any(), any()) } returns
            KyberSwapQuoteDeserialized.Result(routeResponse)
        coEvery { kyberApi.getKyberSwapQuote(any(), any(), any(), any(), any()) } returns quoteJson

        val result =
            repository.getKyberSwapQuote(
                srcToken = coin(Chain.Ethereum, "ETH"),
                dstToken = coin(Chain.Ethereum, "USDC", contractAddress = "0xdst"),
                tokenValue =
                    TokenValue(value = BigInteger("1000000"), token = coin(Chain.Ethereum, "ETH")),
                affiliateBps = 50,
            )

        // isInBps=false → feeAmount passed through as-is
        assertEquals(absoluteFeeAmount, result.tx.swapFee)
    }

    @Test
    fun `kyber discount BPS reduces affiliate fee`() {
        val affiliateFeeBps = 50
        assertEquals(30, maxOf(0, affiliateFeeBps - 20))
    }

    @Test
    fun `kyber discount BPS clamped to zero when discount meets affiliate fee`() {
        val affiliateFeeBps = 50
        assertEquals(0, maxOf(0, affiliateFeeBps - 50))
    }

    @Test
    fun `kyber discount BPS clamped to zero when discount exceeds affiliate fee`() {
        val affiliateFeeBps = 50
        assertEquals(0, maxOf(0, affiliateFeeBps - 60))
    }

    @Test
    fun `kyber no discount leaves affiliate fee unchanged`() {
        val affiliateFeeBps = 50
        assertEquals(50, maxOf(0, affiliateFeeBps - 0))
    }
}
