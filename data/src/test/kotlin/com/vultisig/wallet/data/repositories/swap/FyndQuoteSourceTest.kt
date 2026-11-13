package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.api.models.quotes.FyndFeeBreakdownJson
import com.vultisig.wallet.data.api.models.quotes.FyndOrderQuoteJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteRequestJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteResponseJson
import com.vultisig.wallet.data.api.models.quotes.FyndTransactionJson
import com.vultisig.wallet.data.api.swapAggregators.FyndApi
import com.vultisig.wallet.data.api.swapAggregators.FyndCalldataFixtures
import com.vultisig.wallet.data.api.swapAggregators.FyndConfig
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class FyndQuoteSourceTest {

    private val api = FakeFyndApi()

    @Test
    fun `quotes fee-free in direct debug mode and maps the swap tx`() = runTest {
        val result = source(direct = true).fetch(request(affiliateBps = 50)) as SwapQuoteResult.Evm

        val sent = api.requests.single()
        assertNull(sent.options.encodingOptions.clientFeeParams)
        assertEquals("0.01", sent.options.encodingOptions.slippage)
        assertEquals("transfer_from", sent.options.encodingOptions.transferType)
        assertEquals(ZERO_ADDRESS, sent.orders.single().tokenIn)
        assertEquals(VAULT, sent.orders.single().sender)
        assertEquals(VAULT, sent.orders.single().receiver)

        val tx = result.data.tx
        assertEquals(ROUTER, tx.to)
        assertEquals(AMOUNT_IN.toString(), tx.value)
        assertEquals(219_652L * 2 + 60_000, tx.gas)
        assertEquals((AMOUNT_OUT - ROUTER_FEE).toString(), result.data.dstAmount)
        assertEquals(USDC_CONTRACT, tx.swapFeeTokenContract)
    }

    @Test
    fun `requests the grossed-up fee through the proxy`() = runTest {
        api.respondWith = { feeQuote() }

        source(direct = false, receiver = RECEIVER).fetch(request(affiliateBps = 45))

        val fee = api.requests.single().options.encodingOptions.clientFeeParams
        assertEquals(57, fee?.bps)
        assertEquals(RECEIVER, fee?.receiver)
    }

    @Test
    fun `sends no fee for a holder whose tier discount covers it`() = runTest {
        source(direct = false, receiver = RECEIVER).fetch(request(affiliateBps = 0))

        assertNull(api.requests.single().options.encodingOptions.clientFeeParams)
    }

    @Test
    fun `refuses to quote through the proxy without a fee receiver`() = runTest {
        shouldThrow<SwapException.SwapRouteNotAvailable> {
            source(direct = false, receiver = null).fetch(request(affiliateBps = 50))
        }
        assertEquals(0, api.requests.size)
    }

    @Test
    fun `does nothing while the remote flag is off`() = runTest {
        shouldThrow<SwapException.SwapRouteNotAvailable> {
            source(direct = true, flag = false).fetch(request(affiliateBps = 50))
        }
        assertEquals(0, api.requests.size)
    }

    @Test
    fun `refuses unsupported chains and cross-chain pairs`() = runTest {
        shouldThrow<SwapException.SwapIsNotSupported> {
            source(direct = true).fetch(request(chain = Chain.Polygon))
        }
        shouldThrow<SwapException.SwapIsNotSupported> {
            source(direct = true)
                .fetch(request().copy(dstToken = usdc(Chain.Base)))
        }
    }

    @Test
    fun `gate keeps Fynd out of the candidates until the flag is on and a fee can be signed`() =
        runTest {
            val off = gate(direct = true, receiver = null, flag = false)
            assertEquals(false, off.isUsable())
            assertEquals(false, off.refresh())

            val direct = gate(direct = true, receiver = null, flag = true)
            assertEquals(false, direct.isUsable()) // unknown until the first refresh
            assertEquals(true, direct.refresh())
            assertEquals(true, direct.isUsable())

            assertEquals(false, gate(direct = false, receiver = null, flag = true).refresh())
            assertEquals(true, gate(direct = false, receiver = RECEIVER, flag = true).refresh())
        }

    private fun gate(direct: Boolean, receiver: String?, flag: Boolean): FyndFeatureGate {
        val config = config(direct, receiver)
        return FyndFeatureGate(flags(flag), config)
    }

    private fun config(direct: Boolean, receiver: String?) =
        object : FyndConfig {
            override val baseUrl = FyndConfig.PROXY_BASE_URL
            override val apiKey = if (direct) "key" else null
            override val forceEnabled = false
            override val feeReceiver = receiver
        }

    private fun flags(flag: Boolean) =
        object : FeatureFlagRepository {
            override suspend fun getFeatureFlags() = FeatureFlagJson(isFyndSwapEnabled = flag)
        }

    private fun source(
        direct: Boolean,
        receiver: String? = null,
        flag: Boolean = true,
    ): FyndQuoteSource {
        val config =
            object : FyndConfig {
                override val baseUrl = FyndConfig.PROXY_BASE_URL
                override val apiKey = if (direct) "key" else null
                override val forceEnabled = false
                override val feeReceiver = receiver
            }
        val flags =
            object : FeatureFlagRepository {
                override suspend fun getFeatureFlags() = FeatureFlagJson(isFyndSwapEnabled = flag)
            }
        val clock =
            object : Clock {
                override fun now() = Instant.fromEpochSeconds(FIXTURE_DEADLINE - 20 * 60)
            }
        return FyndQuoteSource(api, config, FyndFeatureGate(flags, config), clock)
    }

    private fun request(chain: Chain = Chain.Ethereum, affiliateBps: Int = 50) =
        SwapQuoteRequest(
            srcToken = eth(chain),
            dstToken = usdc(chain),
            tokenValue = TokenValue(AMOUNT_IN, eth(chain)),
            affiliateBps = affiliateBps,
        )

    private fun feeQuote(): FyndQuoteResponseJson {
        val clientFee = fyndExpectedClientFee(BigInteger.valueOf(AMOUNT_OUT), 57).toLong()
        val afterFees = AMOUNT_OUT - clientFee
        return FyndQuoteResponseJson(
            listOf(
                quote(clientFee = clientFee, minReceived = afterFees - afterFees / 200)
                    .copy(
                        clientFeeReceiver = RECEIVER,
                        clientFeeDeadline = FIXTURE_DEADLINE,
                        transaction =
                            FyndTransactionJson(
                                to = ROUTER,
                                value = AMOUNT_IN.toString(),
                                data = FyndCalldataFixtures.SPLIT_WITH_FEE,
                                clientFeeSignatureOffset = 484,
                            ),
                    )
            )
        )
    }

    private class FakeFyndApi : FyndApi {
        val requests = mutableListOf<FyndQuoteRequestJson>()
        var respondWith: () -> FyndQuoteResponseJson = { FyndQuoteResponseJson(listOf(quote())) }

        override suspend fun quote(chain: Chain, request: FyndQuoteRequestJson): FyndQuoteResponseJson {
            requests += request
            return respondWith()
        }
    }

    private fun eth(chain: Chain) =
        Coin(
            chain = chain,
            ticker = "ETH",
            logo = "",
            address = VAULT,
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun usdc(chain: Chain) =
        eth(chain).copy(ticker = "USDC", decimal = 6, contractAddress = USDC_CONTRACT, isNativeToken = false)

    private companion object {
        const val VAULT = "0x00000000000000000000000000000000000000aa"
        const val RECEIVER = "0x00000000000000000000000000000000000000cc"
        const val ROUTER = "0x1644d2477f809cc2c71bccfd6dc9497e3f83210d"
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        const val USDC_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        val AMOUNT_IN: BigInteger = BigInteger.TEN.pow(18)
        const val AMOUNT_OUT = 1_844_002_345L
        const val ROUTER_FEE = 18_440L
        const val FIXTURE_DEADLINE = 1_800_001_200L

        fun quote(clientFee: Long = 0, minReceived: Long = 1_834_763_986) =
            FyndOrderQuoteJson(
                status = "success",
                amountIn = AMOUNT_IN.toString(),
                amountOut = AMOUNT_OUT.toString(),
                gasEstimate = "219652",
                gasPrice = "108908625",
                transaction =
                    FyndTransactionJson(
                        to = ROUTER,
                        value = AMOUNT_IN.toString(),
                        data = FyndCalldataFixtures.SINGLE_NO_FEE,
                    ),
                feeBreakdown =
                    FyndFeeBreakdownJson(
                        routerFee = if (clientFee == 0L) ROUTER_FEE.toString() else "0",
                        clientFee = clientFee.toString(),
                        maxSlippage = "0",
                        minAmountReceived = minReceived.toString(),
                    ),
            )
    }
}
