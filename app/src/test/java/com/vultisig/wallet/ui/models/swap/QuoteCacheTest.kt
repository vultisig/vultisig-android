package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins that the quote cache key includes the source/destination addresses. The cached tx is
 * address-bound (ERC-20 `tx.data`, SwapKit routing, destination), so a quote built for vault A must
 * not be served to vault B sharing the same pair/amount within the TTL. A regression dropping
 * either address from [QuoteCache.Key] would reintroduce the cross-vault quote bleed; these tests
 * catch it.
 */
internal class QuoteCacheTest {

    // QuoteCache only reads `quote.expiredAt`; a relaxed mock with a future expiry avoids building
    // the full SwapQuote object graph.
    private fun freshQuote(): SwapQuote =
        mockk<SwapQuote> { every { expiredAt } returns Clock.System.now() + 5.minutes }

    @Test
    fun `get returns the cached quote for an identical key`() {
        val cache = QuoteCache()
        val quote = freshQuote()
        cache.put("ETH.ETH", "SOL.SOL", "0xA", "0xB", BigInteger.TEN, SwapProvider.SWAPKIT, quote)

        assertSame(
            quote,
            cache.get("ETH.ETH", "SOL.SOL", "0xA", "0xB", BigInteger.TEN, SwapProvider.SWAPKIT),
        )
    }

    @Test
    fun `get misses when the source address differs (no cross-vault bleed)`() {
        val cache = QuoteCache()
        cache.put(
            "ETH.ETH",
            "SOL.SOL",
            "0xVaultA",
            "0xDst",
            BigInteger.TEN,
            SwapProvider.SWAPKIT,
            freshQuote(),
        )

        // Same pair / amount / provider, different source account (vault B) → must re-fetch.
        assertNull(
            cache.get(
                "ETH.ETH",
                "SOL.SOL",
                "0xVaultB",
                "0xDst",
                BigInteger.TEN,
                SwapProvider.SWAPKIT,
            )
        )
    }

    @Test
    fun `get misses when the destination address differs`() {
        val cache = QuoteCache()
        cache.put(
            "ETH.ETH",
            "SOL.SOL",
            "0xSrc",
            "0xVaultA",
            BigInteger.TEN,
            SwapProvider.SWAPKIT,
            freshQuote(),
        )

        assertNull(
            cache.get(
                "ETH.ETH",
                "SOL.SOL",
                "0xSrc",
                "0xVaultB",
                BigInteger.TEN,
                SwapProvider.SWAPKIT,
            )
        )
    }
}
