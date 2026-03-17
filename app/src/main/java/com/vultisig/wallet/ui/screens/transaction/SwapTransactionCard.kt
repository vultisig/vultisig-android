package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.screens.transaction.components.SendAmountText
import com.vultisig.wallet.ui.screens.transaction.components.ToSeparator
import com.vultisig.wallet.ui.screens.transaction.components.TokenAmountAnnotated
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.screens.transaction.components.TransactionStatusWidget
import com.vultisig.wallet.ui.screens.transaction.components.TypeBadge
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeBadge(
                    iconRes = R.drawable.swap_v2,
                    label = stringResource(R.string.transaction_history_tab_swap),
                )
                TransactionStatusWidget(status = item.status)
            }

            UiSpacer(size = 12.dp)

            if (isInProgress) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TokenCircle(logo = item.fromTokenLogo, ticker = item.fromToken, size = 32)
                    TokenAmountAnnotated(amount = item.fromAmount, token = item.fromToken)
                }

                UiSpacer(size = 8.dp)

                ToSeparator(modifier = Modifier.fillMaxWidth())

                UiSpacer(size = 8.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TokenCircle(logo = item.toTokenLogo, ticker = item.toToken, size = 32)
                    Column {
                        Text(
                            text = stringResource(R.string.transaction_history_min_payout_label),
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.text.tertiary,
                        )
                        TokenAmountAnnotated(amount = item.toAmount, token = item.toToken)
                    }
                }

                if (item.provider.isNotEmpty()) {
                    UiSpacer(size = 8.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f))
                        ViaBadge(provider = item.provider)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenCircle(logo = item.fromTokenLogo, ticker = item.fromToken, size = 24)
                        Column {
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
                        ViaBadge(provider = item.provider)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViaBadge(provider: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.transaction_history_via_label, provider),
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.secondary,
        modifier =
            modifier
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(100.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
