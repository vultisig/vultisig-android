package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SwapQuoteRepositoryImplSameAssetsTest {

    private val repo =
        SwapQuoteRepositoryImpl(
            thorChainApi = mockk(relaxed = true),
            mayaChainApi = mockk(relaxed = true),
            oneInchApi = mockk(relaxed = true),
            liFiChainApi = mockk(relaxed = true),
            jupiterApi = mockk(relaxed = true),
            kyberApi = mockk(relaxed = true),
        )

    private fun evmToken(contractAddress: String) =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xSender",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )

    @Test
    fun `getSwapQuote throws SameAssets when EVM tokens share address case-insensitively`() =
        runTest {
            val checksummed = evmToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
            val lowercased = evmToken("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
            val tokenValue = TokenValue(value = BigInteger.ONE, token = checksummed)

            val ex =
                assertThrows<SwapException> {
                    repo.getSwapQuote(
                        dstAddress = "0xDest",
                        srcToken = checksummed,
                        dstToken = lowercased,
                        tokenValue = tokenValue,
                    )
                }
            assertInstanceOf(SwapException.SameAssets::class.java, ex)
        }

    @Test
    fun `getSwapQuote throws SameAssets for identical EVM tokens`() = runTest {
        val token = evmToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val tokenValue = TokenValue(value = BigInteger.ONE, token = token)

        val ex =
            assertThrows<SwapException> {
                repo.getSwapQuote(
                    dstAddress = "0xDest",
                    srcToken = token,
                    dstToken = token,
                    tokenValue = tokenValue,
                )
            }
        assertInstanceOf(SwapException.SameAssets::class.java, ex)
    }
}
