package com.vultisig.wallet.data.usecases

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

    private val tokenPriceRepository: TokenPriceRepository = mockk()

    private val useCase = SearchRippleTokenUseCaseImpl(tokenPriceRepository)

    @Test
    fun `a ticker resolves to its catalog entry`() = runTest {
        coEvery { tokenPriceRepository.getPriceByPriceProviderId("ripple-usd") } returns
            BigDecimal("1.0")

        val result = useCase("RLUSD.$RLUSD_ISSUER")

        assertEquals("RLUSD", result?.coin?.ticker)
        assertEquals("rlusd", result?.coin?.logo)
        assertEquals("$RLUSD_HEX.$RLUSD_ISSUER", result?.coin?.contractAddress)
        assertEquals(BigDecimal("1.0"), result?.price)
    }

    // A currency with no outstanding supply is absent from the issuer's obligations while its
    // trust lines are live, so issuance is not a precondition for adding one.
    @Test
    fun `an uncatalogued currency resolves without consulting the ledger`() = runTest {
        val result = useCase("USD.$ISSUER")

        assertEquals("USD", result?.coin?.ticker)
        assertEquals("USD.$ISSUER", result?.coin?.contractAddress)
        assertEquals(RIPPLE_TOKEN_DECIMALS, result?.coin?.decimal)
        assertEquals(BigDecimal.ZERO, result?.price)
        coVerify(exactly = 0) { tokenPriceRepository.getPriceByPriceProviderId(any()) }
    }

    // WalletCore uppercases a 3-byte code, so the signer refuses one it would alter; accepting it
    // here would add a token whose every send dies at keysign.
    @Test
    fun `a code the signer would re-case is refused`() = runTest {
        assertNull(useCase("usd.$ISSUER"))
        assertNull(useCase("aUD.$ISSUER"))
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
