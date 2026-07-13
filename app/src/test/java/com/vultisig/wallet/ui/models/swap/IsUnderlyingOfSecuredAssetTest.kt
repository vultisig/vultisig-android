package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class IsUnderlyingOfSecuredAssetTest {

    @Test
    fun `native BTC underlies THORChain secured BTC`() {
        val btc = nativeCoin(Chain.Bitcoin, ticker = "BTC")
        val securedBtc = securedCoin(ticker = "BTC", contractAddress = "btc-btc")

        assertTrue(btc.isUnderlyingOfSecuredAsset(securedBtc))
    }

    @Test
    fun `BSC's chain code differs from its ticker but still matches`() {
        val bnb = nativeCoin(Chain.BscChain, ticker = "BNB")
        val securedBnb = securedCoin(ticker = "BNB", contractAddress = "bsc-bnb")

        assertTrue(bnb.isUnderlyingOfSecuredAsset(securedBnb))
    }

    @Test
    fun `a different underlying ticker does not match`() {
        val eth = nativeCoin(Chain.Ethereum, ticker = "ETH")
        val securedBtc = securedCoin(ticker = "BTC", contractAddress = "btc-btc")

        assertFalse(eth.isUnderlyingOfSecuredAsset(securedBtc))
    }

    @Test
    fun `a ticker outside the SECURE+ eligible set never matches`() {
        val sol = nativeCoin(Chain.Solana, ticker = "SOL")
        val securedSol = securedCoin(ticker = "SOL", contractAddress = "avax-sol-0xfe6b19286")

        assertFalse(sol.isUnderlyingOfSecuredAsset(securedSol))
    }

    @Test
    fun `an ERC20 token never underlies a secured asset`() {
        val usdc = Coin.EMPTY.copy(chain = Chain.Ethereum, ticker = "USDC", isNativeToken = false)
        val securedUsdc = securedCoin(ticker = "USDC", contractAddress = "eth-usdc-0xa0b8")

        assertFalse(usdc.isUnderlyingOfSecuredAsset(securedUsdc))
    }

    private fun nativeCoin(chain: Chain, ticker: String) =
        Coin.EMPTY.copy(chain = chain, ticker = ticker, isNativeToken = true)

    private fun securedCoin(ticker: String, contractAddress: String) =
        Coin.EMPTY.copy(
            chain = Chain.ThorChain,
            ticker = ticker,
            contractAddress = contractAddress,
            isNativeToken = false,
        )
}
