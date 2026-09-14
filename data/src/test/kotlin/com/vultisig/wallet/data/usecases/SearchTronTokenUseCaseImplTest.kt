package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SearchTronTokenUseCaseImplTest {

    private val tronApi: TronApi = mockk()
    private val tokenPriceRepository: TokenPriceRepository = mockk()
    private val useCase = SearchTronTokenUseCaseImpl(tronApi, tokenPriceRepository)

    private val contract = "TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf"

    @Test
    fun `resolves symbol and decimals from ABI-encoded constant results`() = runTest {
        // "USDX" ABI-encoded as a dynamic string: offset, length, then padded UTF-8 bytes.
        val symbolHex =
            "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "5553445800000000000000000000000000000000000000000000000000000000"
        coEvery { tronApi.readContractConstant(contract, "symbol()") } returns symbolHex
        coEvery { tronApi.readContractConstant(contract, "decimals()") } returns
            "0000000000000000000000000000000000000000000000000000000000000006"

        val result = useCase(contract)

        assertEquals("USDX", result?.coin?.ticker)
        assertEquals(6, result?.coin?.decimal)
        assertEquals(BigDecimal.ZERO, result?.price)
    }

    @Test
    fun `unresolvable decimals fails closed instead of guessing`() = runTest {
        coEvery { tronApi.readContractConstant(contract, "symbol()") } returns
            "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000003" +
                "4142430000000000000000000000000000000000000000000000000000000000"
        coEvery { tronApi.readContractConstant(contract, "decimals()") } returns null

        assertNull(useCase(contract))
    }

    @Test
    fun `unresolvable symbol returns null without reading decimals`() = runTest {
        coEvery { tronApi.readContractConstant(contract, "symbol()") } returns null

        assertNull(useCase(contract))
    }

    @Test
    fun `decimals word wider than uint8 fails closed instead of truncating`() = runTest {
        coEvery { tronApi.readContractConstant(contract, "symbol()") } returns
            "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000003" +
                "4142430000000000000000000000000000000000000000000000000000000000"
        // 2^32 + 6: toInt() keeps the low 32 bits and would read this as a plausible 6.
        coEvery { tronApi.readContractConstant(contract, "decimals()") } returns
            "0000000000000000000000000000000000000000000000000000000100000006"

        assertNull(useCase(contract))
    }

    @Test
    fun `full-width decimals word fails closed`() = runTest {
        coEvery { tronApi.readContractConstant(contract, "symbol()") } returns
            "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000003" +
                "4142430000000000000000000000000000000000000000000000000000000000"
        coEvery { tronApi.readContractConstant(contract, "decimals()") } returns "f".repeat(64)

        assertNull(useCase(contract))
    }
}
