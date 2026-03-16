package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.screens.transaction.components.TransactionStatusWidget
import com.vultisig.wallet.ui.screens.transaction.components.TypeBadge
import com.vultisig.wallet.ui.screens.transaction.components.abbreviateAddress
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SendTransactionCard(
    item: TransactionHistoryItemUiModel.Send,
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
                        iconRes = R.drawable.send_2,
                        label = stringResource(R.string.transaction_history_tab_send),
                    )
                    TransactionStatusWidget(status = item.status)
                }

                UiSpacer(size = 12.dp)

                if (isInProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenCircle(logo = item.tokenLogo, ticker = item.token, size = 24)
                        SendAmountText(amount = item.amount, token = item.token)
                    }

                    UiSpacer(size = 8.dp)

                    ToSeparator(modifier = Modifier.fillMaxWidth())

                    UiSpacer(size = 8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.wallet),
                            contentDescription = null,
                            tint = Theme.v2.colors.text.tertiary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = item.toAddress,
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.text.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (!item.provider.isNullOrEmpty()) {
                        UiSpacer(size = 32.dp)
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
                            TokenCircle(logo = item.tokenLogo, ticker = item.token, size = 24)
                            Column {
                                if (!item.fiatValue.isNullOrEmpty()) {
                                    Text(
                                        text = item.fiatValue,
                                        style = Theme.brockmann.supplementary.footnote,
                                        color = Theme.v2.colors.text.primary,
                                    )
                                }
                                SendAmountText(amount = item.amount, token = item.token)
                            }
                        }
                        SendAddressPill(address = item.toAddress.abbreviateAddress())
                    }
                }
            }

            if (isInProgress && !item.provider.isNullOrEmpty()) {
                SendProviderChip(
                    provider = item.provider,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun SendAddressPill(address: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) {
                    append(stringResource(R.string.transaction_history_to_label))
                }
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(address) }
            },
        style = Theme.brockmann.supplementary.caption,
        maxLines = 1,
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.normal, shape = shape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SendProviderChip(provider: String, modifier: Modifier = Modifier) {
    val shape =
        RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 16.dp, bottomStart = 0.dp)
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.button.disabled)) {
                    append(stringResource(R.string.transaction_history_via_prefix))
                }
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(provider) }
            },
        style = Theme.brockmann.supplementary.caption,
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.normal, shape = shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
