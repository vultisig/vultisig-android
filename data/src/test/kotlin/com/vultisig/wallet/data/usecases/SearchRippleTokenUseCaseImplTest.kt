package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SearchRippleTokenUseCaseImplTest {

    private val rippleApi: RippleApi = mockk()
    private val tokenPriceRepository: TokenPriceRepository = mockk()

    private val useCase = SearchRippleTokenUseCaseImpl(rippleApi, tokenPriceRepository)

    @Test
    fun `a ticker the issuer issues resolves to its catalog entry`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(RLUSD_ISSUER) } returns setOf(RLUSD_HEX)
        coEvery { tokenPriceRepository.getPriceByPriceProviderId("ripple-usd") } returns
            BigDecimal("1.0")

        val result = useCase("RLUSD.$RLUSD_ISSUER")

        assertEquals("RLUSD", result?.coin?.ticker)
        assertEquals("rlusd", result?.coin?.logo)
        assertEquals("$RLUSD_HEX.$RLUSD_ISSUER", result?.coin?.contractAddress)
        assertEquals(BigDecimal("1.0"), result?.price)
    }

    @Test
    fun `an uncatalogued currency resolves to a coin at the shared Ripple scale`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(ISSUER) } returns setOf("EUR", "USD")

        val result = useCase("USD.$ISSUER")

        assertEquals("USD", result?.coin?.ticker)
        assertEquals("USD.$ISSUER", result?.coin?.contractAddress)
        assertEquals(RIPPLE_TOKEN_DECIMALS, result?.coin?.decimal)
        assertEquals(BigDecimal.ZERO, result?.price)
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByPriceProviderId(any()) }
    }

    // XRPL compares currency codes byte for byte, so the issuer's own spelling is the only match.
    @Test
    fun `a currency the issuer does not issue returns null`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(ISSUER) } returns setOf("USD")

        assertNull(useCase("usd.$ISSUER"))
        assertNull(useCase("GBP.$ISSUER"))
    }

    // The signer would sign USD instead, so a genuinely-issued lowercase code is still refused —
    // the same gate iOS applies in RippleCustomTokenResolver.
    @Test
    fun `a genuinely issued code the signer would re-case is refused`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(ISSUER) } returns setOf("usd", "aUD")

        assertNull(useCase("usd.$ISSUER"))
        assertNull(useCase("aUD.$ISSUER"))
    }

    @Test
    fun `an account issuing nothing returns null`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(ISSUER) } returns emptySet()

        assertNull(useCase("USD.$ISSUER"))
    }

    @Test
    fun `RPC failure surfaces as null instead of propagating`() = runTest {
        coEvery { rippleApi.fetchIssuedCurrencies(ISSUER) } throws IllegalStateException("offline")

        assertNull(useCase("USD.$ISSUER"))
    }

    @Test
    fun `input that names no currency and issuer pair returns null`() = runTest {
        assertNull(useCase(ISSUER))
        assertNull(useCase("USD.$ISSUER.extra"))
    }

    @Test
    fun `a ticker too long for the 160-bit form returns null`() = runTest {
        assertNull(useCase("TWENTYONECHARACTERSXX.$ISSUER"))
    }

    private companion object {
        const val ISSUER = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
        const val RLUSD_ISSUER = "rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De"
        const val RLUSD_HEX = "524C555344000000000000000000000000000000"
    }
}
