package com.vultisig.wallet.ui.screens.swap.preview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.models.swap.DiscountInfo
import com.vultisig.wallet.ui.models.swap.FeeBreakdown
import com.vultisig.wallet.ui.models.swap.QuoteDisplay
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.datetime.Clock

/**
 * A single preview case for [SwapScreen].
 *
 * @property model the form state to render.
 * @property amount the initial source-amount text field value.
 */
internal data class SwapScreenPreviewCase(val model: SwapFormUiModel, val amount: String = "")

/** Supplies the set of [SwapScreen] preview cases rendered side by side. */
internal class SwapScreenPreviewProvider : PreviewParameterProvider<SwapScreenPreviewCase> {
    override val values: Sequence<SwapScreenPreviewCase>
        get() =
            sequenceOf(
                SwapScreenPreviewCase(
                    model =
                        SwapFormUiModel(quoteDisplay = QuoteDisplay(estimatedDstTokenValue = "0"))
                ),
                SwapScreenPreviewCase(
                    model =
                        SwapFormUiModel(
                            selectedSrcToken = longTokenInput,
                            selectedDstToken = tokenInput,
                            srcFiatValue = "5.25",
                            quoteDisplay =
                                QuoteDisplay(
                                    provider = UiText.DynamicString("ThorSwap"),
                                    estimatedDstTokenValue = "12.80",
                                    estimatedDstFiatValue = "5.24",
                                    expiredAt = Clock.System.now(),
                                ),
                            feeBreakdown =
                                FeeBreakdown(
                                    networkFee = "0.02 RUNE",
                                    networkFeeFiat = "0.004 USD",
                                    totalFee = "0.024 USD",
                                    fee = "0.02 RUNE",
                                ),
                            error = null,
                            formError = null,
                            isSwapDisabled = false,
                            isLoading = false,
                            isLoadingNextScreen = false,
                        )
                ),
                SwapScreenPreviewCase(
                    model =
                        SwapFormUiModel(
                            selectedSrcToken = tokenInput,
                            selectedDstToken = longTokenInput,
                            srcFiatValue = "5.25",
                            quoteDisplay =
                                QuoteDisplay(
                                    provider = UiText.DynamicString("ThorSwap"),
                                    estimatedDstTokenValue = "12.80",
                                    estimatedDstFiatValue = "5.24",
                                    expiredAt = Clock.System.now(),
                                ),
                            feeBreakdown =
                                FeeBreakdown(
                                    networkFee = "0.02 RUNE",
                                    networkFeeFiat = "0.004 USD",
                                    totalFee = "0.024 USD",
                                    fee = "0.02 RUNE",
                                ),
                            error = null,
                            formError = null,
                            isSwapDisabled = false,
                            isLoading = false,
                            isLoadingNextScreen = false,
                        )
                ),
                SwapScreenPreviewCase(
                    model =
                        SwapFormUiModel(
                            selectedSrcToken = longTokenInput,
                            selectedDstToken = longTokenInput,
                            srcFiatValue = "5.25",
                            quoteDisplay =
                                QuoteDisplay(
                                    provider = UiText.DynamicString("ThorSwap"),
                                    estimatedDstTokenValue = "12.80",
                                    estimatedDstFiatValue = "5.24",
                                    expiredAt = Clock.System.now(),
                                ),
                            feeBreakdown =
                                FeeBreakdown(
                                    networkFee = "0.02 RUNE",
                                    networkFeeFiat = "0.004 USD",
                                    totalFee = "0.024 USD",
                                    fee = "0.02 RUNE",
                                ),
                            discountInfo =
                                DiscountInfo(
                                    referralBpsDiscount = 10,
                                    referralBpsDiscountFiatValue = "0.001 USD",
                                    vultBpsDiscount = 30,
                                    vultBpsDiscountFiatValue = "0.003 USD",
                                    tierType = TierType.GOLD,
                                ),
                            error = null,
                            formError = null,
                            isSwapDisabled = false,
                            isLoading = false,
                            isLoadingNextScreen = false,
                        )
                ),
            )
}

@Preview
@Composable
internal fun SwapFormScreenPreview(
    @PreviewParameter(SwapScreenPreviewProvider::class) case: SwapScreenPreviewCase
) {
    SwapScreen(state = case.model, srcAmountTextFieldState = TextFieldState(case.amount))
}

// Mid-load snapshot for #4712: amount entered, firm quote still resolving. Before the change the
// destination blanked to a skeleton here; after, it shows a greyed indicative estimate.
@Preview
@Composable
internal fun SwapFormQuoteLoadingPreview() {
    SwapScreen(
        state =
            SwapFormUiModel(
                selectedSrcToken = longTokenInput,
                selectedDstToken = tokenInput,
                srcFiatValue = "5.25",
                quoteDisplay =
                    QuoteDisplay(
                        provider = UiText.Empty,
                        estimatedDstTokenValue = "12.80",
                        estimatedDstFiatValue = "$5.24",
                        isDstEstimated = true,
                    ),
                isLoading = true,
                isSwapDisabled = true,
            ),
        srcAmountTextFieldState = TextFieldState("2.5"),
    )
}

private val longTokenInput =
    TokenBalanceUiModel(
        model =
            SendSrc(
                address =
                    Address(
                        chain = Chain.ThorChain,
                        address = "thor1xyzabc123",
                        accounts =
                            listOf(
                                Account(
                                    token =
                                        Coin(
                                            chain = Chain.ThorChain,
                                            ticker = "RUNE",
                                            logo =
                                                "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
                                            address = "thor1xyzabc123",
                                            decimal = 8,
                                            hexPublicKey = "0xabc123def456",
                                            priceProviderID = "thorchain-rune",
                                            contractAddress = "",
                                            isNativeToken = true,
                                        ),
                                    tokenValue = TokenValue(BigInteger("2500000000"), "RUNE", 8),
                                    fiatValue = FiatValue(BigDecimal("5.25"), "USD"),
                                    price = FiatValue(BigDecimal("2.10"), "USD"),
                                )
                            ),
                    ),
                account =
                    Account(
                        token =
                            Coin(
                                chain = Chain.ThorChain,
                                ticker = "RUNE",
                                logo =
                                    "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
                                address = "thor1xyzabc123",
                                decimal = 8,
                                hexPublicKey = "0xabc123def456",
                                priceProviderID = "thorchain-rune",
                                contractAddress = "",
                                isNativeToken = true,
                            ),
                        tokenValue = TokenValue(BigInteger("2500000000"), "RUNE", 8),
                        fiatValue = FiatValue(BigDecimal("5.25"), "USD"),
                        price = FiatValue(BigDecimal("2.10"), "USD"),
                    ),
            ),
        title = "LP-THOR.RUJI/ ETH.USDC-XYK",
        balance = "0.11412095",
        fiatValue = "5.25",
        isNativeToken = true,
        isLayer2 = false,
        tokenStandard = "THORCHAIN",
        tokenLogo = "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
        chainLogo = Chain.ThorChain.logo,
    )

private val tokenInput =
    TokenBalanceUiModel(
        model =
            SendSrc(
                address =
                    Address(
                        chain = Chain.TerraClassic,
                        address = "maya1def456ghi789",
                        accounts =
                            listOf(
                                Account(
                                    token =
                                        Coin(
                                            chain = Chain.TerraClassic,
                                            ticker = "CACAO",
                                            logo =
                                                "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
                                            address = "maya1def456ghi789",
                                            decimal = 6,
                                            hexPublicKey = "0xdef789ghi012",
                                            priceProviderID = "mayachain-cacao",
                                            contractAddress = "",
                                            isNativeToken = true,
                                        ),
                                    tokenValue = TokenValue(BigInteger("1000000000"), "CACAO", 6),
                                    fiatValue = FiatValue(BigDecimal("4.10"), "USD"),
                                    price = FiatValue(BigDecimal("0.41"), "USD"),
                                )
                            ),
                    ),
                account =
                    Account(
                        token =
                            Coin(
                                chain = Chain.MayaChain,
                                ticker = "CACAO",
                                logo =
                                    "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
                                address = "maya1def456ghi789",
                                decimal = 6,
                                hexPublicKey = "0xdef789ghi012",
                                priceProviderID = "mayachain-cacao",
                                contractAddress = "",
                                isNativeToken = true,
                            ),
                        tokenValue = TokenValue(BigInteger("1000000000"), "CACAO", 6),
                        fiatValue = FiatValue(BigDecimal("4.10"), "USD"),
                        price = FiatValue(BigDecimal("0.41"), "USD"),
                    ),
            ),
        title = "CACAO",
        balance = "10.0",
        fiatValue = "4.10",
        isNativeToken = true,
        isLayer2 = false,
        tokenStandard = "THORCHAIN",
        tokenLogo = "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
        chainLogo = Chain.ThorChain.logo,
    )
