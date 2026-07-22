package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.getChain
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Regression coverage for issue #5323 (chain-identity single-source).
 *
 * Two failure shapes, both a per-chain lookup with a silent wrong-value fallback:
 * 1. [String.getChain] silently resolved any unknown ticker to ThorChain on a withdrawal path.
 * 2. `Chain.coinType` derives symbol/decimals from WalletCore, which are wrong for chains that
 *    share a WalletCore CoinType — [Coins] is the single source and must win via every reachable
 *    accessor ([Chain.nativeTokenTicker], [Chain.nativeToken], [Chain.toValue]).
 */
class ChainIdentitySourceTest {

    @Test
    fun `getChain resolves known native tickers`() {
        assertEquals(Chain.ThorChain, "RUNE".getChain())
        assertEquals(Chain.MayaChain, "CACAO".getChain())
        assertEquals(Chain.Bittensor, "TAO".getChain())
        assertEquals(Chain.Qbtc, "QBTC".getChain())
    }

    @Test
    fun `getChain throws on unknown ticker instead of defaulting to ThorChain`() {
        val error = assertThrows(IllegalStateException::class.java) { "NOTATICKER".getChain() }
        // The whole point of #5323: an unrecognised ticker must NOT silently become ThorChain.
        assertEquals(false, error.message?.contains("ThorChain"))
    }

    @Test
    fun `unknown ticker never silently resolves to any chain`() {
        listOf("", "XYZ", "rune", "Cacao", "UNKNOWN").forEach { ticker ->
            assertThrows(IllegalStateException::class.java) { ticker.getChain() }
        }
    }

    @Test
    fun `nativeTokenTicker sources display symbol from Coins for shared-CoinType chains`() {
        assertEquals("CACAO", Chain.MayaChain.nativeTokenTicker)
        assertEquals("TAO", Chain.Bittensor.nativeTokenTicker)
        assertEquals("QBTC", Chain.Qbtc.nativeTokenTicker)
    }

    @Test
    fun `nativeToken decimals come from Coins for shared-CoinType chains`() {
        // WalletCore coinType.decimals is 8 / 10 / 6 for these (a 100x / 10x / 100x error) — Coins
        // is the single source and must win.
        assertEquals(10, Chain.MayaChain.nativeToken.decimal)
        assertEquals(9, Chain.Bittensor.nativeToken.decimal)
        assertEquals(8, Chain.Qbtc.nativeToken.decimal)
    }

    @Test
    fun `Chain toValue converts using Coins decimals not WalletCore decimals`() {
        // 1 CACAO = 10^10 base units; the buggy 10^8 path would return 100.
        assertEquals(0, BigDecimal.ONE.compareTo(Chain.MayaChain.toValue(BigInteger.TEN.pow(10))))
        // 1 TAO = 10^9 base units; the buggy 10^10 path would return 0.1.
        assertEquals(0, BigDecimal.ONE.compareTo(Chain.Bittensor.toValue(BigInteger.TEN.pow(9))))
        // 1 QBTC = 10^8 base units; the buggy 10^6 path would return 100.
        assertEquals(0, BigDecimal.ONE.compareTo(Chain.Qbtc.toValue(BigInteger.TEN.pow(8))))
    }

    @Test
    fun `every chain has exactly one native token in Coins`() {
        Chain.entries.forEach { chain ->
            val natives = Coins.coins[chain]?.filter { it.isNativeToken }.orEmpty()
            assertEquals(1, natives.size, "Chain $chain must have exactly one native token")
        }
    }
}
