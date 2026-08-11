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
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByContactAddress(any(), any(), any()) }
    }

    @Test
    fun `falls back to the contract address when nothing is cached`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(RUNE.id, AppCurrency.USD) } returns null
        coEvery {
            tokenPriceRepository.getPriceByPriceProviderId(RUNE.priceProviderID, AppCurrency.USD)
        } returns BigDecimal.ZERO
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(
                RUNE.chain.id,
                RUNE.contractAddress,
                AppCurrency.USD,
            )
        } returns BigDecimal("3")

        val fiat = calculator.createFiatValue(BigDecimal("2"), RUNE, AppCurrency.USD)

        assertEquals(BigDecimal("6.00"), fiat.value)
    }

    /**
     * THORChain has no CoinGecko asset-platform id and no LI.FI chain id, so the contract route can
     * only return zero for an `x/…` denom — RUJI has to resolve through its CoinGecko id instead.
     */
    @Test
    fun `an uncached coin resolves through its price provider id before the contract address`() =
        runTest {
            coEvery { tokenPriceRepository.getCachedPrice(RUJI.id, AppCurrency.USD) } returns null
            coEvery {
                tokenPriceRepository.getPriceByPriceProviderId(
                    RUJI.priceProviderID,
                    AppCurrency.USD,
                )
            } returns BigDecimal("0.18")

            val fiat = calculator.createFiatValue(BigDecimal("2"), RUJI, AppCurrency.USD)

            assertEquals(BigDecimal("0.36"), fiat.value)
            coVerify(exactly = 0) {
                tokenPriceRepository.getPriceByContactAddress(any(), any(), any())
            }
        }

    @Test
    fun `a price provider id that resolves to nothing still falls through to the contract`() =
        runTest {
            coEvery { tokenPriceRepository.getCachedPrice(RUJI.id, AppCurrency.USD) } returns null
            coEvery {
                tokenPriceRepository.getPriceByPriceProviderId(
                    RUJI.priceProviderID,
                    AppCurrency.USD,
                )
            } returns BigDecimal.ZERO
            coEvery {
                tokenPriceRepository.getPriceByContactAddress(
                    RUJI.chain.id,
                    RUJI.contractAddress,
                    AppCurrency.USD,
                )
            } returns BigDecimal("0.5")

            val fiat = calculator.createFiatValue(BigDecimal("2"), RUJI, AppCurrency.USD)

            assertEquals(BigDecimal("1.00"), fiat.value)
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

    /**
     * A pool asset the catalogue knows is priced through its curated coin, so it picks up that
     * coin's CoinGecko id. Going straight to the contract route left every pool leg the vault
     * doesn't hold at zero on the chains that route can't answer for.
     */
    @Test
    fun `an uncached pool asset resolves through its curated coin's price provider id`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        coEvery {
            tokenPriceRepository.getPriceByPriceProviderId(
                Coins.Ethereum.USDC.priceProviderID,
                AppCurrency.USD,
            )
        } returns BigDecimal("1.0")

        val fiat =
            calculator.createFiatValueFromPoolAsset(
                amount = BigDecimal("3"),
                chain = Chain.Ethereum,
                // Lowercased on purpose: the pool names the contract in a different case than the
                // catalogue's checksummed form, and the match has to survive that.
                contractAddress = Coins.Ethereum.USDC.contractAddress.lowercase(),
                ticker = "USDC",
                currency = AppCurrency.USD,
            )

        assertEquals(BigDecimal("3.00"), fiat.value)
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByContactAddress(any(), any(), any()) }
    }

    /** A native pool asset carries no contract address, so only the curated coin can price it. */
    @Test
    fun `a native pool asset with no contract address still resolves through its curated coin`() =
        runTest {
            coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
            coEvery {
                tokenPriceRepository.getPriceByPriceProviderId(
                    Coins.Bitcoin.BTC.priceProviderID,
                    AppCurrency.USD,
                )
            } returns BigDecimal("60000")

            val fiat =
                calculator.createFiatValueFromPoolAsset(
                    amount = BigDecimal("0.5"),
                    chain = Chain.Bitcoin,
                    ticker = "BTC",
                    contractAddress = "",
                    currency = AppCurrency.USD,
                )

            assertEquals(BigDecimal("30000.00"), fiat.value)
        }

    @Test
    fun `a pool asset the catalogue doesn't carry falls back to its contract address`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(
                Chain.Ethereum.id,
                UNKNOWN_CONTRACT,
                AppCurrency.USD,
            )
        } returns BigDecimal("4")

        val fiat =
            calculator.createFiatValueFromPoolAsset(
                amount = BigDecimal("3"),
                chain = Chain.Ethereum,
                ticker = "NOTACOIN",
                contractAddress = UNKNOWN_CONTRACT,
                currency = AppCurrency.USD,
            )

        assertEquals(BigDecimal("12.00"), fiat.value)
    }

    /**
     * sTCY carries TCY's `tcy` price-provider id, so the id route would price it at bare TCY parity
     * — everything the staking position has compounded, silently dropped. Only the contract route
     * applies the NAV correction.
     */
    @Test
    fun `a NAV-priced receipt skips the borrowed price provider id`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(STCY.id, AppCurrency.USD) } returns null
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(
                STCY.chain.id,
                STCY.contractAddress,
                AppCurrency.USD,
            )
        } returns BigDecimal("1.4")

        val fiat = calculator.createFiatValue(BigDecimal("2"), STCY, AppCurrency.USD)

        assertEquals(BigDecimal("2.80"), fiat.value)
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByPriceProviderId(any(), any()) }
    }

    /**
     * The lookup is quoted in the currency the caller captured, not in whatever the app currency
     * happens to be by the time it resolves — the two disagree across a currency switch, and the
     * contract route persists what it is handed.
     */
    @Test
    fun `the captured currency travels down to every lookup`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(RUJI.id, AppCurrency.EUR) } returns null
        coEvery {
            tokenPriceRepository.getPriceByPriceProviderId(RUJI.priceProviderID, AppCurrency.EUR)
        } returns BigDecimal("0.2")

        val fiat = calculator.createFiatValue(BigDecimal("2"), RUJI, AppCurrency.EUR)

        assertEquals(BigDecimal("0.40"), fiat.value)
        assertEquals("EUR", fiat.currency)
    }

    private companion object {
        val RUNE = Coins.ThorChain.RUNE
        val RUJI = Coins.ThorChain.RUJI
        val STCY = Coins.ThorChain.sTCY
        const val UNKNOWN_CONTRACT = "0x00000000000000000000000000000000deadbeef"
    }
}
