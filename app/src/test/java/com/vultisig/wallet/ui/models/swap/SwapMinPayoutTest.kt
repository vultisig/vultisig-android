package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Which routes may be said to enforce a minimum output, and what that minimum is once rescaled out
 * of THORChain's 1e8 accounting into the destination coin's own units (#5711).
 */
internal class SwapMinPayoutTest {

    @Test
    fun `a thorchain memo's limit rescales into the destination coin's units`() {
        val minPayout =
            signedMinimumOutput(
                payload = thorPayload(tcy),
                memo = "=:THOR.TCY:thor1uet6qz79tu:321308705:va:40",
                dstToken = tcy,
            )

        minPayout?.value shouldBe BigInteger.valueOf(321308705)
        minPayout?.decimal shouldBe BigDecimal("3.21308705")
    }

    @Test
    fun `the limit is 1e8 regardless of the destination chain's own precision`() {
        // THORChain quotes and memos are 1e8 everywhere, so an 18-decimal destination must be
        // rescaled up rather than read as wei.
        val minPayout =
            signedMinimumOutput(
                payload = thorPayload(eth),
                memo = "=:ETH.ETH:0xabc:321308705:va:40",
                dstToken = eth,
            )

        minPayout?.decimal shouldBe BigDecimal("3.21308705")
        minPayout?.value shouldBe BigInteger("3213087050000000000")
    }

    @Test
    fun `a maya memo's limit is read the same way`() {
        val minPayout =
            signedMinimumOutput(
                payload = SwapPayload.MayaChain(thorSwapPayload(cacao)),
                memo = "=:MAYA.CACAO:maya1abc:32130870500:va:40",
                dstToken = cacao,
            )

        // Maya denominates CACAO in its own 1e10 rather than THORChain's 1e8.
        minPayout?.decimal shouldBe BigDecimal("3.21308705")
        minPayout?.value shouldBe BigInteger.valueOf(32_130_870_500)
    }

    @Test
    fun `a memo naming another asset is no floor for this one`() {
        // The memo and the destination coin reach a cosigner as separately decoded halves of the
        // request. A LIM denominated in CACAO must never be labelled with TCY's ticker.
        signedMinimumOutput(
            payload = thorPayload(tcy),
            memo = "=:MAYA.CACAO:maya1abc:321308705:va:40",
            dstToken = tcy,
        ) shouldBe null
    }

    @Test
    fun `a spelling this app cannot resolve still yields the floor it can read`() {
        // THORChain resolves `e.eth` against its live pool list; refusing every name we can't
        // expand would hide floors that are really enforced.
        val minPayout =
            signedMinimumOutput(
                payload = thorPayload(eth),
                memo = "=:e.eth:0xabc:321308705:va:40",
                dstToken = eth,
            )

        minPayout?.decimal shouldBe BigDecimal("3.21308705")
    }

    @Test
    fun `an auto-slippage memo enforces nothing`() {
        signedMinimumOutput(
            payload = thorPayload(tcy),
            memo = "=:THOR.TCY:thor1uet6qz79tu::va:40",
            dstToken = tcy,
        ) shouldBe null
    }

    @Test
    fun `a route with no memo at all enforces nothing`() {
        signedMinimumOutput(payload = thorPayload(tcy), memo = null, dstToken = tcy) shouldBe null
    }

    @Test
    fun `a provider-built route is never credited with a floor`() {
        // SwapKit hands over a pre-built transaction; whatever its own router enforces is not
        // visible to us, so a memo-shaped string must not be read as a guarantee.
        signedMinimumOutput(
            payload =
                SwapPayload.SwapKit(
                    SwapKitSwapPayloadJson(
                        fromCoin = eth,
                        toCoin = tcy,
                        fromAmount = BigInteger.TEN,
                        toAmountDecimal = BigDecimal.ONE,
                        txType = "PSBT",
                        txPayload = ByteArray(0),
                        targetAddress = "bc1qDeposit",
                        subProvider = "GARDEN",
                    )
                ),
            memo = "=:THOR.TCY:thor1uet6qz79tu:321308705:va:40",
            dstToken = tcy,
        ) shouldBe null
    }

    private fun thorPayload(dstToken: Coin) = SwapPayload.ThorChain(thorSwapPayload(dstToken))

    private fun thorSwapPayload(dstToken: Coin) =
        THORChainSwapPayload(
            fromAddress = "thor1Owner",
            fromCoin = rune,
            toCoin = dstToken,
            vaultAddress = "thor1Vault",
            routerAddress = null,
            fromAmount = BigInteger.TEN,
            toAmountDecimal = BigDecimal.ONE,
            toAmountLimit = "0",
            streamingInterval = "1",
            streamingQuantity = "0",
            expirationTime = 0uL,
            isAffiliate = true,
        )

    private companion object {
        val rune =
            Coin(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                logo = "rune",
                address = "thor1Owner",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "thorchain",
                contractAddress = "",
                isNativeToken = true,
            )
        val tcy =
            Coin(
                chain = Chain.ThorChain,
                ticker = "TCY",
                logo = "tcy",
                address = "thor1Owner",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "tcy",
                contractAddress = "tcy",
                isNativeToken = false,
            )
        val cacao =
            Coin(
                chain = Chain.MayaChain,
                ticker = "CACAO",
                logo = "cacao",
                address = "maya1Owner",
                decimal = 10,
                hexPublicKey = "hex",
                priceProviderID = "cacao",
                contractAddress = "",
                isNativeToken = true,
            )
        val eth =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "eth",
                address = "0xOwner",
                decimal = 18,
                hexPublicKey = "hex",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
