@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapperImpl
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The Swap Fee row is titled with the list rate (0.50%), so its amount is the undiscounted fee: the
 * affiliate fee the provider charged plus the discounts itemized on the rows below it. Showing the
 * charged fee under a list-rate title read as two cuts of the same fee — a Gold vault saw "Swap Fee
 * (0.30%) $0.59" and "VULT (Gold -20 bps) -$0.39" as if both applied, while Total Fees used only
 * the $0.59.
 */
internal class SwapQuotePipelineSwapFeeRowTest {

    private val swapDiscountChecker: SwapDiscountChecker = mockk()

    private val pipeline =
        SwapQuotePipeline(
            swapQuoteRepository = mockk(relaxed = true),
            appCurrencyRepository = mockk(relaxed = true),
            referralRepository = mockk(relaxed = true),
            getDiscountBpsUseCase = mockk(relaxed = true),
            convertTokenAndValueToTokenValue = mockk(relaxed = true),
            swapQuoteManager = mockk(relaxed = true),
            swapDiscountChecker = swapDiscountChecker,
            swapGasCalculator = mockk(relaxed = true),
            swapValidator = mockk(relaxed = true),
            fiatValueToString =
                FiatValueToStringMapperImpl(
                    mockk<AppCurrencyRepository>().also {
                        coEvery { it.getCurrencyFormat() } returns
                            NumberFormat.getCurrencyInstance(Locale.US)
                    }
                ),
        )

    @Test
    fun `grosses the charged fee up to the list rate by the tier discount`() = runTest {
        // 200 USDC at Gold: 30 bps went on the wire ($0.59 charged), and the row below claims the
        // remaining 20 bps of the $200 source ($0.40). The row shows the $0.99 the swap would have
        // cost undiscounted, so the two rows subtract back to the $0.59 in Total Fees.
        discountedAt(20, TierType.GOLD)

        val result = buildSuccess(charged = "0.59", srcFiat = "200")

        result.feeText shouldBe "$0.99"
        // Total Fees stays net — the charged fee, untouched.
        result.swapFeeFiat.value.compareTo(BigDecimal("0.59")) shouldBe 0
    }

    @Test
    fun `prices the row off the source when the tier waived the whole fee`() = runTest {
        // The Ultimate tier pays no affiliate fee at all, so there is nothing to scale from — but
        // the row still has to disclose the 50 bps the tier waived, and its discount row -$1.00
        // still has to reconcile to a $0.00 net.
        discountedAt(50, TierType.ULTIMATE)

        val result = buildSuccess(charged = "0.00", srcFiat = "200")

        result.feeText shouldBe "$1.00"
    }

    @Test
    fun `leaves the row at the charged fee for a vault with no discount`() = runTest {
        discountedAt(null, null)

        val result = buildSuccess(charged = "0.99", srcFiat = "200")

        result.feeText shouldBe "$0.99"
    }

    @Test
    fun `leaves a SwapKit inbound fee alone rather than grossing a network cost`() = runTest {
        // SwapKit itemizes its source-chain inbound (deposit) cost as the quote's fee and bakes the
        // affiliate fee into the quoted destination amount, so there is no affiliate charge here to
        // gross up: adding the tier discount would invent a discount on a network cost, and titling
        // that cost "0.50%" would be wrong however it is valued.
        discountedAt(20, TierType.GOLD)

        val result =
            buildSuccess(charged = "0.59", srcFiat = "200", provider = SwapProvider.SWAPKIT)

        result.feeText shouldBe "$0.59"
        result.swapFeePercent shouldBe null
        // And no discount row either: it would subtract from a fee it was never added to.
        result.discountInfo.vultBpsDiscountFiatValue shouldBe null
    }

    @Test
    fun `drops the rate and the discount row when the source has no price`() = runTest {
        // Nothing can be added back to the charged fee, so it stays as the provider quoted it —
        // and "0.50%" over a discounted amount would restate the double-billing read this row
        // exists to fix. The discount row would read "-$0.00" against a fee it never came off.
        discountedAt(20, TierType.GOLD)

        val result = buildSuccess(charged = "0.59", srcFiat = "0")

        result.feeText shouldBe "$0.59"
        result.swapFeePercent shouldBe null
        result.discountInfo.vultBpsDiscountFiatValue shouldBe null
    }

    @Test
    fun `keeps the discount row when the source is priced`() = runTest {
        discountedAt(20, TierType.GOLD)

        val result = buildSuccess(charged = "0.59", srcFiat = "200")

        result.swapFeePercent shouldBe "0.50%"
        result.discountInfo.vultBpsDiscountFiatValue shouldBe "$0.40"
    }

    private fun discountedAt(bps: Int?, tier: TierType?) {
        coEvery { swapDiscountChecker.checkVultBpsDiscount(any(), any(), any()) } returns
            VultDiscountResult(bps, bps?.let { "$0.40" }, tier)
    }

    private suspend fun buildSuccess(
        charged: String,
        srcFiat: String,
        provider: SwapProvider = SwapProvider.LIFI,
    ) =
        pipeline.buildSuccess(
            bestQuote = bestQuote(charged = charged, srcFiat = srcFiat, provider = provider),
            src = sendSrc(usdc),
            srcTokenValue = BigInteger.valueOf(200_000_000),
            tokenValue = TokenValue(BigInteger.valueOf(200_000_000), usdc),
            currentDiscountInfo = DiscountInfo(),
        )

    private fun bestQuote(
        charged: String,
        srcFiat: String,
        provider: SwapProvider = SwapProvider.LIFI,
    ): BestQuote {
        val chargedFiat = FiatValue(BigDecimal(charged), "USD")
        return BestQuote(
            candidate = QuoteCandidate(provider, vultBPSDiscount = 20, referral = null),
            result =
                QuoteFetchResult(
                    quote = mockk<SwapQuote>(relaxed = true),
                    provider = provider,
                    providerUiText = UiText.DynamicString("LI.FI"),
                    srcFiatValueText = "$$srcFiat",
                    estimatedDstTokenValue = "0",
                    estimatedDstFiatValue = "0",
                    comparableDstFiat = BigDecimal.ZERO,
                    feeText = "$$charged",
                    swapFeeFiat = chargedFiat,
                    affiliateFeeFiat = chargedFiat,
                    srcFiat = FiatValue(BigDecimal(srcFiat), "USD"),
                    swapFeePercent = "0.50%",
                ),
        )
    }

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xaddr",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "usd-coin",
            contractAddress = "0xusdc",
            isNativeToken = false,
        )

    private fun sendSrc(coin: Coin): SendSrc {
        val account =
            Account(
                token = coin,
                tokenValue = TokenValue(BigInteger.valueOf(1_000_000_000L), coin),
                fiatValue = null,
                price = null,
            )
        return SendSrc(
            Address(chain = coin.chain, address = coin.address, accounts = listOf(account)),
            account,
        )
    }
}
