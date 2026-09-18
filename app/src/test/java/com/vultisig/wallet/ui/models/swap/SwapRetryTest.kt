package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
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
    fun `a market swap row resolves to the held pair and its raw amount`() {
        val retry = row().toSwapRetry(listOf(eth, btc))

        retry.shouldNotBeNull()
        retry.srcToken shouldBe eth
        retry.dstToken shouldBe btc
        retry.srcAmount shouldBe "0.5"
    }

    @Test
    fun `the route carries the pair, the amount and the verify-on-quote flag`() {
        val route = row().toSwapRetry(listOf(eth, btc)).shouldNotBeNull().toRoute("vault-1")

        route.vaultId shouldBe "vault-1"
        route.chainId shouldBe Chain.Ethereum.id
        route.srcTokenId shouldBe eth.id
        route.dstTokenId shouldBe btc.id
        route.srcAmount shouldBe "0.5"
        route.externalRecipient.shouldBeNull()
        route.verifyOnQuote shouldBe true
    }

    @Test
    fun `an output routed to a chosen address is routed there again`() {
        // Without this the form would reopen with the recipient off and pay the vault — a
        // different destination from the one the user approved the first time.
        val route =
            row(externalRecipient = "bc1qsomeoneelse")
                .toSwapRetry(listOf(eth, btc))
                .shouldNotBeNull()
                .toRoute("vault-1")

        route.externalRecipient shouldBe "bc1qsomeoneelse"
    }

    @Test
    fun `a row whose destination the recording device could not read offers no retry`() {
        // A co-signer sees a SwapKit route as opaque bytes: the output may have gone to the vault
        // or elsewhere, and a retry would have to pick one.
        row(isRecipientUnknown = true).toSwapRetry(listOf(eth, btc)).shouldBeNull()
    }

    @Test
    fun `a limit order is never retried as a market swap`() {
        row(isLimitOrder = true).toSwapRetry(listOf(eth, btc)).shouldBeNull()
    }

    @Test
    fun `a dApp-authored swap is never retried through the form`() {
        // The done screen hides the button from the live payload's dappMetadata; the row carries
        // the same verdict so History agrees with it once the payload is gone.
        row(isDappRequest = true).toSwapRetry(listOf(eth, btc)).shouldBeNull()
    }

    @Test
    fun `a legacy row without a raw amount offers no retry`() {
        // Its display amount is abbreviated and locale-formatted; guessing a number from it could
        // stage a trade a thousand times the size of the one that failed.
        row(fromAmountDecimal = "").toSwapRetry(listOf(eth, btc)).shouldBeNull()
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
        fromAmountDecimal: String = "0.5",
        isLimitOrder: Boolean = false,
        isDappRequest: Boolean = false,
        externalRecipient: String? = null,
        isRecipientUnknown: Boolean = false,
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
            fromAmountDecimal = fromAmountDecimal,
            isDappRequest = isDappRequest,
            externalRecipient = externalRecipient,
            isRecipientUnknown = isRecipientUnknown,
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
