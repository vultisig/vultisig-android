@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
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
    fun `fetchBestQuote surfaces dust error over generic no-route error`() = runTest {
        // convertTokenValueToFiat is a relaxed suspend function-type mock; relaxed answers for
        // such interfaces return a bare Object that can't be cast to FiatValue, throwing a
        // ClassCastException before getQuote runs. Stub it so both providers reach getQuote and
        // surface their typed dust/no-route errors.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)
        coEvery { swapQuoteRepository.getQuote(SwapProvider.MAYA, any()) } throws
            SwapException.AmountBelowDustThreshold("amount below dust threshold")
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } throws
            SwapException.SwapRouteNotAvailable("no route available")

        val manager = createManager()
        // THORCHAIN is listed first so that reverting to first-by-provider-order would surface
        // the generic SwapRouteNotAvailable instead of MAYA's actionable dust error.
        val result = runCatching {
            manager.fetchBestQuote(
                candidates =
                    listOf(
                        QuoteCandidate(
                            provider = SwapProvider.THORCHAIN,
                            vultBPSDiscount = null,
                            referral = null,
                        ),
                        QuoteCandidate(
                            provider = SwapProvider.MAYA,
                            vultBPSDiscount = null,
                            referral = null,
                        ),
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

        assertIs<SwapException.AmountBelowDustThreshold>(result.exceptionOrNull())
    }
}
