package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.swap.MayaQuoteSource
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.ThorChainQuoteSource
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SwapQuoteRepositoryImplSameAssetsTest {

    private val thorChain = ThorChainQuoteSource(mockk())
    private val maya = MayaQuoteSource(mockk())

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
    fun `thorChain throws SameAssets when EVM tokens share address case-insensitively`() = runTest {
        val checksummed = evmToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val lowercased = evmToken("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val tokenValue = TokenValue(value = BigInteger.ONE, token = checksummed)

        val ex =
            assertThrows<SwapException> {
                thorChain.fetch(
                    SwapQuoteRequest(
                        srcToken = checksummed,
                        dstToken = lowercased,
                        tokenValue = tokenValue,
                        dstAddress = "0xDest",
                    )
                )
            }
        assertInstanceOf(SwapException.SameAssets::class.java, ex)
    }

    @Test
    fun `thorChain throws SameAssets for identical EVM tokens`() = runTest {
        val token = evmToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val tokenValue = TokenValue(value = BigInteger.ONE, token = token)

        val ex =
            assertThrows<SwapException> {
                thorChain.fetch(
                    SwapQuoteRequest(
                        srcToken = token,
                        dstToken = token,
                        tokenValue = tokenValue,
                        dstAddress = "0xDest",
                    )
                )
            }
        assertInstanceOf(SwapException.SameAssets::class.java, ex)
    }

    @Test
    fun `maya throws SameAssets for identical EVM tokens`() = runTest {
        val token = evmToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val tokenValue = TokenValue(value = BigInteger.ONE, token = token)

        val ex =
            assertThrows<SwapException> {
                maya.fetch(
                    SwapQuoteRequest(
                        srcToken = token,
                        dstToken = token,
                        tokenValue = tokenValue,
                        dstAddress = "0xDest",
                    )
                )
            }
        assertInstanceOf(SwapException.SameAssets::class.java, ex)
    }
}
