@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson.Companion.TX_TYPE_CARDANO_PREBUILT
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson.Companion.TX_TYPE_PSBT
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test

/**
 * Covers the SwapKit swap-fee zeroing in [SwapQuotePipeline.buildSuccess] (#5321).
 *
 * SwapKit UTXO-family sources (Bitcoin PSBT deposit, Cardano CBOR deposit) settle by broadcasting a
 * deposit whose on-chain miner fee is the only network cost and is already surfaced on the Network
 * Fee row. SwapKit reports that same deposit cost as its wire inbound fee, so counting it again as
 * a swap fee would double-count the source-chain network cost. iOS discards the wire inbound fee
 * for both families and shows the cost once as Network Fee, so both Bitcoin and Cardano zero the
 * swap fee (the earlier Cardano carve-out reopened the double count).
 */
internal class SwapQuotePipelineSwapKitFeeTest {

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
        )

    init {
        coEvery { swapDiscountChecker.checkVultBpsDiscount(any(), any(), any()) } returns
            VultDiscountResult(null, null, null)
    }

    private val swapFee = FiatValue(BigDecimal("1.50"), "USD")

    @Test
    fun `zeroes the SwapKit swap fee for a Bitcoin source (double-counted as the UTXO plan fee)`() =
        runTest {
            val btc = coin(Chain.Bitcoin)
            val result =
                pipeline.buildSuccess(
                    bestQuote = bestSwapKitQuote(btc, TX_TYPE_PSBT),
                    src = sendSrc(btc),
                    srcTokenValue = BigInteger.valueOf(1_000),
                    tokenValue = TokenValue(BigInteger.valueOf(1_000), btc),
                    currentDiscountInfo = DiscountInfo(),
                )

            result.swapFeeFiat.value shouldBe BigDecimal.ZERO
            result.swapFeeFiat.currency shouldBe "USD"
            // Empty text hides the swap-fee breakdown row entirely (matches iOS).
            result.feeText shouldBe ""
        }

    @Test
    fun `zeroes the SwapKit swap fee for a Cardano source (its deposit fee is the Network Fee) (#5321)`() =
        runTest {
            val ada = coin(Chain.Cardano)
            val result =
                pipeline.buildSuccess(
                    bestQuote = bestSwapKitQuote(ada, TX_TYPE_CARDANO_PREBUILT),
                    src = sendSrc(ada),
                    srcTokenValue = BigInteger.valueOf(1_000),
                    tokenValue = TokenValue(BigInteger.valueOf(1_000), ada),
                    currentDiscountInfo = DiscountInfo(),
                )

            result.swapFeeFiat.value shouldBe BigDecimal.ZERO
            result.swapFeeFiat.currency shouldBe "USD"
            // Empty text hides the swap-fee breakdown row entirely (matches iOS).
            result.feeText shouldBe ""
            // Cardano is excluded from the Bitcoin UTXO plan-fee path; the flat send-style fee owns
            // the Network Fee row.
            result.isUtxoSwap shouldBe false
        }

    private fun bestSwapKitQuote(srcToken: Coin, txType: String): BestQuote {
        val quote =
            SwapQuote.SwapKit(
                expectedDstValue = TokenValue(BigInteger.valueOf(400), srcToken),
                fees = TokenValue(BigInteger.valueOf(9), srcToken),
                expiredAt = Clock.System.now(),
                data =
                    SwapKitSwapPayloadJson(
                        fromCoin = srcToken,
                        toCoin = srcToken,
                        fromAmount = BigInteger.valueOf(1_000),
                        toAmountDecimal = BigDecimal("0.004"),
                        txType = txType,
                        txPayload = ByteArray(0),
                        targetAddress = "deposit-address",
                    ),
                subProvider = "THORChain",
            )
        return BestQuote(
            candidate =
                QuoteCandidate(SwapProvider.SWAPKIT, vultBPSDiscount = null, referral = null),
            result =
                QuoteFetchResult(
                    quote = quote,
                    provider = SwapProvider.SWAPKIT,
                    providerUiText = UiText.DynamicString("SwapKit"),
                    srcFiatValueText = "0",
                    estimatedDstTokenValue = "0",
                    estimatedDstFiatValue = "0",
                    comparableDstFiat = BigDecimal.ZERO,
                    feeText = "$1.50",
                    swapFeeFiat = swapFee,
                ),
        )
    }

    private fun coin(chain: Chain) =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = "addr",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = chain.raw,
            contractAddress = "",
            isNativeToken = true,
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
