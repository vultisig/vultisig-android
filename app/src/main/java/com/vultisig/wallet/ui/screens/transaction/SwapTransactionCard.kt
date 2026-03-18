package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel.Broadcasted
import com.vultisig.wallet.ui.models.TransactionStatusUiModel.Confirmed
import com.vultisig.wallet.ui.screens.transaction.components.SendAmountText
import com.vultisig.wallet.ui.screens.transaction.components.ToSeparator
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.screens.transaction.components.TransactionStatusWidget
import com.vultisig.wallet.ui.screens.transaction.components.TypeBadge
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SwapTransactionCard(
    item: TransactionHistoryItemUiModel.Swap,
    modifier: Modifier = Modifier,
) {
    val isInProgress =
        item.status is TransactionStatusUiModel.Broadcasted ||
            item.status is TransactionStatusUiModel.Pending

    V2Container(
        modifier = modifier,
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(
                        iconRes = R.drawable.swap,
                        label = stringResource(R.string.transaction_type_button_swap),
                    )
                    TransactionStatusWidget(status = item.status)
                }

                if (isInProgress) {
                    UiSpacer(size = 20.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TokenCircle(logo = item.fromTokenLogo, ticker = item.fromToken, size = 24)
                        SwapAmountText(amount = item.fromAmount, token = item.fromToken)
                    }

                    UiSpacer(size = 8.dp)

                    ToSeparator(modifier = Modifier.fillMaxWidth())

                    UiSpacer(size = 8.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TokenCircle(logo = item.toTokenLogo, ticker = item.toToken, size = 24)
                        Column {
                            Text(
                                text =
                                    stringResource(R.string.transaction_history_min_payout_label),
                                style = Theme.brockmann.supplementary.captionSmall,
                                color = Theme.v2.colors.text.tertiary,
                            )
                            SwapAmountText(amount = item.toAmount, token = item.toToken)
                        }
                    }

                    if (item.provider.isNotEmpty()) {
                        UiSpacer(size = 32.dp)
                    }
                } else {
                    UiSpacer(size = 12.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenCircle(logo = item.fromTokenLogo, ticker = item.fromToken, size = 24)
                        Column(modifier = Modifier.weight(1f)) {
                            if (!item.fiatValue.isNullOrEmpty()) {
                                Text(
                                    text = item.fiatValue,
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.primary,
                                )
                            }
                            SendAmountText(amount = item.fromAmount, token = item.fromToken)
                        }
                    }

                    if (item.provider.isNotEmpty()) {
                        UiSpacer(size = 32.dp)
                    }
                }
            }

            if (item.provider.isNotEmpty()) {
                ViaBadge(provider = item.provider, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
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

@Composable
private fun ViaBadge(provider: String, modifier: Modifier = Modifier) {
    val shape =
        RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
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
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.normal, shape = shape)
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    )
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
private fun PreviewSwapCardCompleted() {
    OnBoardingComposeTheme {
        SwapTransactionCard(
            item = previewSwapItem.copy(status = Confirmed),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
