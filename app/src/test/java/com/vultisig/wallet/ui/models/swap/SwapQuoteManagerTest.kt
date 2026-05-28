@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SwapQuoteManagerTest {

    private val swapQuoteRepository: SwapQuoteRepository = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk(relaxed = true)
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)
    private val fiatValueToString: FiatValueToStringMapper = mockk(relaxed = true)
    private val searchToken: SearchTokenUseCase = mockk(relaxed = true)
    private val convertTokenToTokenUseCase: ConvertTokenToToken = mockk(relaxed = true)

    private fun createManager() =
        SwapQuoteManager(
            swapQuoteRepository = swapQuoteRepository,
            tokenRepository = tokenRepository,
            convertTokenValueToFiat = convertTokenValueToFiat,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            fiatValueToString = fiatValueToString,
            searchToken = searchToken,
            convertTokenToTokenUseCase = convertTokenToTokenUseCase,
        )

    @Test
    fun `fetchBestQuote converts all-timeout failures to SwapException_TimeOut`() = runTest {
        coEvery { tokenRepository.getNativeToken(any()) } coAnswers
            {
                delay(Long.MAX_VALUE)
                error("unreachable")
            }

        val manager = createManager()
        val deferred = async {
            runCatching {
                manager.fetchBestQuote(
                    candidates =
                        listOf(
                            QuoteCandidate(
                                provider = SwapProvider.THORCHAIN,
                                vultBPSDiscount = null,
                                referral = null,
                            )
                        ),
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = mockk(relaxed = true),
                    dstToken = mockk(relaxed = true),
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = mockk(relaxed = true),
                    currency = AppCurrency.USD,
                    amount = BigDecimal.ONE,
                )
            }
        }

        advanceTimeBy(15_001L)

        assertIs<SwapException.TimeOut>(deferred.await().exceptionOrNull())
    }

    @Test
    fun `fetchQuote keeps the SwapKit sub-provider label across a cache hit (Native path)`() =
        runTest {
            // Pins the SwapQuoteResult.Native arm + the `is SwapQuote.SwapKit -> subProvider`
            // label:
            // a non-EVM SwapKit quote must surface "SwapKit (NEAR)", and that must survive a cache
            // hit (second fetch skips the lambda) rather than collapse to the generic "SwapKit".
            val btc = coin(Chain.Bitcoin, "BTC", address = "bc1qsrc", decimals = 8)
            val eth = coin(Chain.Ethereum, "ETH", address = "0xdst", decimals = 18)
            val swapKitQuote =
                SwapQuote.SwapKit(
                    expectedDstValue = TokenValue(BigInteger.ONE, eth),
                    fees = TokenValue(BigInteger.valueOf(400), btc),
                    expiredAt = Clock.System.now().plus(5.minutes),
                    data = mockk(relaxed = true),
                    subProvider = "NEAR",
                )
            coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
                SwapQuoteResult.Native(swapKitQuote)
            // Relaxed mockk returns Object for these fun-type mappers; give them real return types
            // so the SwapKit (null-rawFees) fiat-formatting path doesn't ClassCastException.
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"

            val manager = createManager()
            val first =
                manager.fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    btc,
                    eth,
                    BigInteger.ONE,
                    TokenValue(BigInteger.ONE, btc),
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )
            val second =
                manager.fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    btc,
                    eth,
                    BigInteger.ONE,
                    TokenValue(BigInteger.ONE, btc),
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )

            assertEquals(UiText.DynamicString("SwapKit (NEAR)"), first.providerUiText)
            assertEquals(UiText.DynamicString("SwapKit (NEAR)"), second.providerUiText)
            // Second call is a cache hit (same pair/amount/addresses) — the lambda is skipped.
            coVerify(exactly = 1) { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) }
        }

    private fun coin(chain: Chain, ticker: String, address: String, decimals: Int) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = address,
            decimal = decimals,
            hexPublicKey = "pub",
            priceProviderID = ticker.lowercase(),
            contractAddress = "",
            isNativeToken = true,
        )
}
