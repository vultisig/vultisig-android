package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.models.TransactionFailureExplanation
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel.Broadcasted
import com.vultisig.wallet.ui.models.TransactionStatusUiModel.Confirmed
import com.vultisig.wallet.ui.models.TransactionStatusUiModel.Pending
import com.vultisig.wallet.ui.screens.transaction.components.ToSeparator
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.screens.transaction.components.TransactionStatusWidget
import com.vultisig.wallet.ui.screens.transaction.components.TypeBadge
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText

@Composable
internal fun SwapTransactionCard(
    item: TransactionHistoryItemUiModel.Swap,
    modifier: Modifier = Modifier,
) {
    val isInProgress =
        item.status is TransactionStatusUiModel.Broadcasted ||
            item.status is TransactionStatusUiModel.Pending
    val failureExplanation = (item.status as? TransactionStatusUiModel.Failed)?.explanation

    V2Container(
        modifier = modifier,
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(),
    ) {
        Box {
            Column(modifier = Modifier.padding(swapCardPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(
                        iconRes = R.drawable.swap,
                        label = stringResource(R.string.transaction_type_button_swap),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TransactionStatusWidget(status = item.status, timestamp = item.timestamp)
                        if (failureExplanation != null) {
                            Text(
                                text = stringResource(failureExplanation.labelRes),
                                style = Theme.brockmann.supplementary.caption,
                                color = Theme.v2.colors.alerts.error,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }

                if (isInProgress) {
                    UiSpacer(size = 22.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TokenCircle(logo = item.fromTokenLogo, ticker = item.fromToken, size = 24)
                        SwapAmountText(amount = item.fromAmount, token = item.fromToken)
                    }

                    TokenRowConnector()

                    ToSeparator(modifier = Modifier.fillMaxWidth())

                    TokenRowConnector()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TokenCircle(logo = item.toTokenLogo, ticker = item.toToken, size = 24)
                        Column {
                            Text(
                                text =
                                    stringResource(
                                        if (item.isLimitOrder)
                                            R.string.transaction_history_min_payout_label
                                        else R.string.transaction_history_expected_payout_label
                                    ),
                                style = Theme.brockmann.supplementary.captionSmall,
                                color = Theme.v2.colors.text.tertiary,
                            )
                            SwapAmountText(amount = item.toAmount, token = item.toToken)
                        }
                    }

                    if (item.provider.isNotEmpty()) {
                        UiSpacer(size = 15.dp)
                    }
                } else {
                    UiSpacer(size = 12.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SwapPairLogo(
                            fromLogo = item.fromTokenLogo,
                            fromToken = item.fromToken,
                            toLogo = item.toTokenLogo,
                            toToken = item.toToken,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            // The destination leg leads: what the swap produced is what the row
                            // exists to answer, and it is the half the user could not see before.
                            SwapLegText(
                                amount = "+${item.toAmount}",
                                token = item.toToken,
                                color = Theme.v2.colors.text.primary,
                            )
                            SwapLegText(
                                amount = "-${item.fromAmount}",
                                token = item.fromToken,
                                color = Theme.v2.colors.text.tertiary,
                            )
                        }
                        SwapPairPill(fromToken = item.fromToken, toToken = item.toToken)
                    }
                }
            }

            // The pair pill states the route on a settled card, so the badge would be a second
            // answer to the same question in the corner it already occupies. It stays on an
            // in-progress card, whose row has no pill.
            if (isInProgress && item.provider.isNotEmpty()) {
                ViaBadge(
                    provider = item.provider,
                    providerLogo = item.providerLogo,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun TokenRowConnector() {
    Box(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        Box(
            modifier =
                Modifier.padding(start = 11.5.dp)
                    .width(1.dp)
                    .height(12.dp)
                    .background(color = Theme.v2.colors.border.light)
        )
    }
}

@Composable
private fun SwapAmountText(amount: String, token: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(amount) }
                append(" ")
                withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) { append(token) }
            },
        style = Theme.brockmann.body.s.medium,
        modifier = modifier,
    )
}

/**
 * One leg of a settled swap: `+2.5 ETH` or `-4,210.00 USDC`. Both halves of the line carry the same
 * colour — the destination leg reads as the outcome, the source leg as the receipt beneath it.
 */
@Composable
private fun SwapLegText(
    amount: String,
    token: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$amount $token",
        style = Theme.brockmann.supplementary.footnote,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The two assets as one coin: the facing half of each logo, so the pair reads as a single mark at
 * the same 24dp a one-sided row spends on its single logo.
 */
@Composable
private fun SwapPairLogo(
    fromLogo: ImageModel,
    fromToken: String,
    toLogo: ImageModel,
    toToken: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SwapPairLogoHalf(logo = fromLogo, ticker = fromToken, half = Alignment.CenterStart)
        SwapPairLogoHalf(logo = toLogo, ticker = toToken, half = Alignment.CenterEnd)
    }
}

@Composable
private fun SwapPairLogoHalf(logo: ImageModel, ticker: String, half: Alignment) {
    Box(
        modifier =
            Modifier.size(width = swapPairLogoSize / 2, height = swapPairLogoSize).clipToBounds(),
        contentAlignment = half,
    ) {
        // requiredSize, not size: the logo has to keep its full diameter against a window half its
        // width, which is precisely what makes the visible edge a half-circle rather than a
        // squeeze.
        TokenCircle(
            modifier = Modifier.requiredSize(swapPairLogoSize),
            logo = logo,
            ticker = ticker,
            size = SWAP_PAIR_LOGO_SIZE,
        )
    }
}

/** `USDC → SOL`, the route the card is about, in the corner the badge used to hold. */
@Composable
private fun SwapPairPill(fromToken: String, toToken: String, modifier: Modifier = Modifier) {
    Text(
        text = "$fromToken → $toToken",
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.primary,
        maxLines = 1,
        modifier =
            modifier
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = Theme.v2.radius.pill,
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = Theme.v2.radius.pill,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val viaBadgeVerticalPadding = 8.dp

/** The provider logo — the tallest thing the badge ever holds, so it sets the badge's height. */
private const val VIA_BADGE_CONTENT_SIZE = 16

private val swapCardPadding = 16.dp

/** Diameter of the paired coin logos; each contributes the half of itself that faces the other. */
private const val SWAP_PAIR_LOGO_SIZE = 24

private val swapPairLogoSize = SWAP_PAIR_LOGO_SIZE.dp

@Composable
private fun ViaBadge(provider: String, providerLogo: ImageModel?, modifier: Modifier = Modifier) {
    // The badge is anchored to the card's bottom-end corner, so its outer corner is not its own
    // geometry — it is the card's, and has to be read from the same token or the badge cuts across
    // the curve it is meant to follow.
    val shape =
        RoundedCornerShape(
            topStart = Theme.v2.radius.md.size,
            topEnd = 0.dp,
            bottomEnd = Theme.v2.radius.xl.size,
            bottomStart = 0.dp,
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .padding(
                    start = 8.dp,
                    end = 16.dp,
                    top = viaBadgeVerticalPadding,
                    bottom = viaBadgeVerticalPadding,
                ),
    ) {
        if (providerLogo != null) {
            TokenCircle(logo = providerLogo, ticker = provider, size = VIA_BADGE_CONTENT_SIZE)
        }
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) {
                        append(stringResource(R.string.transaction_history_via_prefix))
                        append(" ")
                    }
                    withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(provider) }
                },
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

private val previewSwapItem =
    TransactionHistoryItemUiModel.Swap(
        id = "1",
        txHash = "0xabc123",
        chain = "Ethereum",
        status = Confirmed,
        explorerUrl = "",
        timestamp = System.currentTimeMillis(),
        fromToken = "RUNE",
        fromAmount = "1,000.12",
        fromChain = "THORChain",
        fromTokenLogo = R.drawable.rune,
        toToken = "WBTC",
        toAmount = "0.1251",
        toChain = "Bitcoin",
        toTokenLogo = R.drawable.bitcoin,
        provider = "THORChain",
        providerLogo = R.drawable.rune,
        fiatValue = "$3,847.50",
        fromAddress = null,
        toAddress = null,
        feeEstimate = null,
    )

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapCardInProgress() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item = previewSwapItem.copy(status = Broadcasted),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapCardPending() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item =
                previewSwapItem.copy(
                    status = Pending,
                    timestamp = System.currentTimeMillis() - 5_000L,
                ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapCardFailedOnSlippage() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item =
                previewSwapItem.copy(
                    status =
                        TransactionStatusUiModel.Failed(
                            reason = UiText.DynamicString("Insufficient output"),
                            explanation = TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE,
                        ),
                    provider = "LI.FI",
                ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapCardFailedWithoutExplanation() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item =
                previewSwapItem.copy(
                    status =
                        TransactionStatusUiModel.Failed(
                            reason = UiText.DynamicString("Transaction reverted")
                        )
                ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapCardCompleted() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item = previewSwapItem.copy(status = Confirmed),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
