package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * A history row is resolved against the vault's live coins, never trusted on its own: the row names
 * a ticker and a chain, and the form needs the exact coin — or nothing, if there is any doubt which
 * one it was (#5918).
 */
internal class SwapRetryTest {

    @Test
    fun `a market swap row resolves to the held pair`() {
        val retry = row().toSwapRetry(listOf(eth, btc))

        retry.shouldNotBeNull()
        retry.srcToken shouldBe eth
        retry.dstToken shouldBe btc
    }

    @Test
    fun `the route carries the pair and nothing else`() {
        val route = row().toSwapRetry(listOf(eth, btc)).shouldNotBeNull().toRoute("vault-1")

        route shouldBe
            Route.Swap(
                vaultId = "vault-1",
                chainId = Chain.Ethereum.id,
                srcTokenId = eth.id,
                dstTokenId = btc.id,
            )
    }

    @Test
    fun `a limit order is never retried as a market swap`() {
        row(isLimitOrder = true).toSwapRetry(listOf(eth, btc)).shouldBeNull()
    }

    @Test
    fun `a side the vault no longer holds hides the retry`() {
        row().toSwapRetry(listOf(eth)).shouldBeNull()
        row().toSwapRetry(listOf(btc)).shouldBeNull()
    }

    @Test
    fun `the contract address picks between two same-ticker tokens on one chain`() {
        val curatedUsdc = token("USDC", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val customUsdc = token("USDC", "0xCustomUsdcContract")
        val row =
            row(fromToken = "USDC", fromContractAddress = customUsdc.contractAddress.uppercase())

        val retry = row.toSwapRetry(listOf(curatedUsdc, customUsdc, btc))

        retry.shouldNotBeNull().srcToken shouldBe customUsdc
    }

    @Test
    fun `a legacy row without a contract address is matched on ticker and chain`() {
        // Rows recorded before the address was stored can still name their pair; the button is
        // worth more on them than on nothing, as long as the match is unambiguous.
        val usdc = token("USDC", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")

        row(fromToken = "USDC").toSwapRetry(listOf(usdc, btc)).shouldNotBeNull().srcToken shouldBe
            usdc
    }

    @Test
    fun `a legacy row that fits two same-ticker tokens offers no retry`() {
        val curatedUsdc = token("USDC", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
        val customUsdc = token("USDC", "0xCustomUsdcContract")

        row(fromToken = "USDC").toSwapRetry(listOf(curatedUsdc, customUsdc, btc)).shouldBeNull()
    }

    @Test
    fun `a contract address is matched exactly where its case is part of the address`() {
        val jetton = token("USDT", "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs", Chain.Ton)
        val row =
            row(
                fromToken = "USDT",
                fromChain = Chain.Ton.id,
                fromContractAddress = jetton.contractAddress.lowercase(),
            )

        row.toSwapRetry(listOf(jetton, btc)).shouldBeNull()
    }

    private fun row(
        fromToken: String = "ETH",
        fromChain: String = Chain.Ethereum.id,
        fromContractAddress: String = "",
        isLimitOrder: Boolean = false,
    ) =
        SwapTransactionHistoryData(
            fromToken = fromToken,
            fromAmount = "0.5",
            fromChain = fromChain,
            fromTokenLogo = "",
            toToken = "BTC",
            toAmount = "0.01",
            toChain = Chain.Bitcoin.id,
            toTokenLogo = "",
            provider = "THORChain",
            fiatValue = "$1,000",
            toContractAddress = "",
            toIsNative = true,
            isLimitOrder = isLimitOrder,
            fromContractAddress = fromContractAddress,
        )

    private fun token(ticker: String, contractAddress: String, chain: Chain = Chain.Ethereum) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "0xvault",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xvault",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private val btc =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1qvault",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )
}
