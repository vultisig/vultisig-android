package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the fiat conversion the Thorchain and Maya position screens now share. Both used to hold
 * their own copy; these tests pin the single implementation for both callers.
 */
internal class DefiFiatValueCalculatorTest {

    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var calculator: DefiFiatValueCalculator

    @BeforeEach
    fun setUp() {
        tokenPriceRepository = mockk(relaxed = true)
        calculator = DefiFiatValueCalculator(tokenPriceRepository)
    }

    @Test
    fun `prices an amount from the cached price`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(RUNE.id, AppCurrency.USD) } returns
            BigDecimal("2.5")

        val fiat = calculator.createFiatValue(BigDecimal("4"), RUNE, AppCurrency.USD)

        assertEquals(BigDecimal("10.00"), fiat.value)
        assertEquals("USD", fiat.currency)
    }

    @Test
    fun `truncates fractional cents instead of rounding up`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(RUNE.id, AppCurrency.USD) } returns
            BigDecimal("0.999")

        val fiat = calculator.createFiatValue(BigDecimal("1"), RUNE, AppCurrency.USD)

        assertEquals(BigDecimal("0.99"), fiat.value)
    }

    @Test
    fun `an exact zero amount short-circuits before any price lookup`() = runTest {
        val fiat = calculator.createFiatValue(BigDecimal.ZERO, RUNE, AppCurrency.USD)

        assertEquals(BigDecimal.ZERO, fiat.value)
        coVerify(exactly = 0) { tokenPriceRepository.getCachedPrice(any(), any()) }
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByContactAddress(any(), any()) }
    }

    @Test
    fun `falls back to the contract address when nothing is cached`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(RUNE.id, AppCurrency.USD) } returns null
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(RUNE.chain.id, RUNE.contractAddress)
        } returns BigDecimal("3")

        val fiat = calculator.createFiatValue(BigDecimal("2"), RUNE, AppCurrency.USD)

        assertEquals(BigDecimal("6.00"), fiat.value)
    }

    @Test
    fun `a failed price lookup yields zero rather than propagating`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } throws
            RuntimeException("price service down")

        val fiat = calculator.createFiatValue(BigDecimal("2"), RUNE, AppCurrency.USD)

        assertEquals(BigDecimal.ZERO, fiat.value)
        assertEquals("USD", fiat.currency)
    }

    @Test
    fun `cancellation is never swallowed by the failure path`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } throws
            CancellationException("scope closed")

        assertFailsWith<CancellationException> {
            calculator.createFiatValue(BigDecimal("2"), RUNE, AppCurrency.USD)
        }
    }

    @Test
    fun `a pool asset is keyed by ticker and chain id`() = runTest {
        coEvery {
            tokenPriceRepository.getCachedPrice("BTC-${Chain.Bitcoin.id}", AppCurrency.USD)
        } returns BigDecimal("100")

        val fiat =
            calculator.createFiatValueFromPoolAsset(
                amount = BigDecimal("0.5"),
                chain = Chain.Bitcoin,
                ticker = "BTC",
                contractAddress = "",
                currency = AppCurrency.USD,
            )

        assertEquals(BigDecimal("50.00"), fiat.value)
    }

    @Test
    fun `an uncached pool asset falls back to its contract address`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(Chain.Ethereum.id, CONTRACT)
        } returns BigDecimal("4")

        val fiat =
            calculator.createFiatValueFromPoolAsset(
                amount = BigDecimal("3"),
                chain = Chain.Ethereum,
                ticker = "USDC",
                contractAddress = CONTRACT,
                currency = AppCurrency.USD,
            )

        assertEquals(BigDecimal("12.00"), fiat.value)
    }

    private companion object {
        val RUNE = Coins.ThorChain.RUNE
        const val CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    }
}
