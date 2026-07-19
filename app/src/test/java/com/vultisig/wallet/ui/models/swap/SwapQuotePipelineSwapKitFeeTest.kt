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
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test

/**
 * Covers the SwapKit swap-fee zeroing in [SwapQuotePipeline.buildSuccess] (#5321).
 *
 * SwapKit BTC broadcasts the provider's PSBT whose miner fee is already counted as the UTXO plan
 * network fee, so the duplicated inbound swap fee is zeroed to avoid double-counting. Cardano rides
 * in `TokenStandard.UTXO` but is not a secp256k1/PSBT UTXO chain and does not go through the UTXO
 * plan-fee path, so its swap fee must be shown, not zeroed.
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
            fiatValueToString = mockk(relaxed = true),
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

            assertEquals(BigDecimal.ZERO, result.swapFeeFiat.value)
            assertEquals("USD", result.swapFeeFiat.currency)
        }

    @Test
    fun `keeps the SwapKit swap fee for a Cardano source (not a PSBT UTXO plan-fee swap) (#5321)`() =
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

            assertEquals(swapFee, result.swapFeeFiat)
            assertTrue(result.swapFeeFiat.value.signum() > 0)
            // Cardano is not routed through the UTXO plan-fee path either.
            assertTrue(!result.isUtxoSwap)
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
