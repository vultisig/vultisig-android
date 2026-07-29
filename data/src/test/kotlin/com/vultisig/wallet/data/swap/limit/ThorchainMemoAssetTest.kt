package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThorchainMemoAssetTest {

    @Test
    fun `native coins encode as CHAIN_TICKER`() {
        assertEquals("ETH.ETH", coin(Chain.Ethereum, "ETH", native = true).thorchainMemoAsset())
        assertEquals("BTC.BTC", coin(Chain.Bitcoin, "BTC", native = true).thorchainMemoAsset())
        assertEquals("THOR.RUNE", coin(Chain.ThorChain, "RUNE", native = true).thorchainMemoAsset())
    }

    @Test
    fun `Cosmos native always encodes as GAIA_ATOM`() {
        assertEquals("GAIA.ATOM", coin(Chain.GaiaChain, "ATOM", native = true).thorchainMemoAsset())
    }

    @Test
    fun `Noble routes under the NOBLE prefix, not its USDC fee ticker`() {
        // Chain.swapAssetName() maps Noble to USDC; the memo prefix must be THORChain's NOBLE.
        assertEquals("NOBLE.USDC", coin(Chain.Noble, "USDC", native = true).thorchainMemoAsset())
    }

    @Test
    fun `ERC20 tokens abbreviate the contract to its last 6 chars, uppercased`() {
        val usdc =
            coin(
                Chain.Ethereum,
                "USDC",
                native = false,
                contract = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            )
        assertEquals("ETH.USDC-06EB48", usdc.thorchainMemoAsset())
    }

    @Test
    fun `rejects a chain THORChain cannot route`() {
        assertThrows(IllegalArgumentException::class.java) {
            coin(Chain.Sui, "SUI", native = true).thorchainMemoAsset()
        }
    }

    @Test
    fun `rejects a token whose contract is too short to abbreviate`() {
        assertThrows(IllegalArgumentException::class.java) {
            coin(Chain.Ethereum, "X", native = false, contract = "0x12").thorchainMemoAsset()
        }
    }

    @Test
    fun `routability table and its inverse do not drift`() {
        assertTrue(isThorchainRoutable(Chain.Bitcoin))
        assertTrue(isThorchainRoutable(Chain.Noble))
        assertFalse(isThorchainRoutable(Chain.MayaChain))
        assertFalse(isThorchainRoutable(Chain.Cardano))
        thorchainMemoAssetChainPrefix.forEach { (chain, prefix) ->
            assertEquals(chain, thorchainAssetPrefixToChain[prefix])
        }
    }

    private fun coin(chain: Chain, ticker: String, native: Boolean, contract: String = "") =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = native,
        )
}
