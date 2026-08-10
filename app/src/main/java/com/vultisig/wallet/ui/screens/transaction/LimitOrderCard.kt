package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.models.limitorder.LimitOrderHistoryStatus
import com.vultisig.wallet.ui.models.limitorder.LimitOrderHistoryUiModel
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

/**
 * One resting or closed THORChain limit order.
 *
 * The Cancel action lives on the card because that is the only place an order exists in the app —
 * there is no separate order detail screen, and a resting order is precisely the thing a user comes
 * to the history looking for.
 */
@Composable
internal fun LimitOrderCard(
    item: LimitOrderHistoryUiModel,
    onCancelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Text(
                    text = stringResource(R.string.limit_order_card_title),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
                LimitOrderStatusChip(status = item.status)
            }

            UiSpacer(size = 12.dp)

            Text(
                text =
                    buildAnnotatedString {
                        // An order whose precision was never recorded shows the pair alone. Better
                        // no amount than one printed in the coin's smallest units.
                        item.sellAmount?.let { amount ->
                            withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) {
                                append(amount)
                            }
                            append(" ")
                        }
                        withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) {
                            append(item.sellTicker)
                        }
                        withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) {
                            append(" → ")
                        }
                        withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) {
                            append(item.buyTicker)
                        }
                    },
                style = Theme.brockmann.body.s.medium,
            )

            UiSpacer(size = 8.dp)

            LimitOrderDetailRow(
                label = stringResource(R.string.limit_order_target_price_label),
                value =
                    stringResource(
                        R.string.limit_order_target_price_value,
                        item.sellTicker,
                        item.targetPrice,
                        item.buyTicker,
                    ),
            )

            item.expiry?.let { expiry ->
                UiSpacer(size = 4.dp)
                LimitOrderDetailRow(
                    label = stringResource(R.string.limit_order_expiry_label),
                    value = expiry.asString(),
                )
            }

            item.fillPercent?.let { percent ->
                UiSpacer(size = 4.dp)
                LimitOrderDetailRow(
                    label = stringResource(R.string.limit_order_filled_label),
                    value = stringResource(R.string.limit_order_filled_value, percent),
                )
            }

            if (item.hasCancelDuplicate) {
                UiSpacer(size = 8.dp)
                Text(
                    text = stringResource(R.string.limit_order_cancel_duplicate_warning),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.warning,
                )
            }

            if (item.isCancellable) {
                UiSpacer(size = 12.dp)
                VsButton(
                    label = stringResource(R.string.limit_order_cancel_button),
                    variant = VsButtonVariant.Error,
                    size = VsButtonSize.Small,
                    onClick = { onCancelClick(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (item.cancelBlockedReason != null) {
                // A disabled action with no reason is worse than no action at all, so the blocker
                // is
                // spelled out instead of the button being silently withheld.
                UiSpacer(size = 12.dp)
                Text(
                    text = item.cancelBlockedReason.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}

@Composable
private fun LimitOrderDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        Text(
            text = value,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun LimitOrderStatusChip(status: LimitOrderHistoryStatus) {
    val label =
        when (status) {
            LimitOrderHistoryStatus.Resting -> R.string.limit_order_status_resting
            // Never styled as success: THORChain accepts a cancel that matches nothing, so the
            // order
            // may still be live and may still fill.
            LimitOrderHistoryStatus.Cancelling -> R.string.limit_order_status_cancelling
            LimitOrderHistoryStatus.Filled -> R.string.limit_order_status_filled
            LimitOrderHistoryStatus.Expired -> R.string.limit_order_status_expired
            LimitOrderHistoryStatus.Cancelled -> R.string.limit_order_status_cancelled
            LimitOrderHistoryStatus.Refunded -> R.string.limit_order_status_refunded
        }
    val color =
        when (status) {
            LimitOrderHistoryStatus.Resting,
            LimitOrderHistoryStatus.Cancelling -> Theme.v2.colors.alerts.warning
            LimitOrderHistoryStatus.Filled -> Theme.v2.colors.alerts.success
            LimitOrderHistoryStatus.Expired,
            LimitOrderHistoryStatus.Cancelled,
            LimitOrderHistoryStatus.Refunded -> Theme.v2.colors.text.tertiary
        }
    Text(
        text = stringResource(label),
        style = Theme.brockmann.supplementary.caption,
        color = color,
        modifier =
            Modifier.background(color = color.copy(alpha = 0.12f), shape = Theme.v2.radius.sm)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private val previewOrder =
    LimitOrderHistoryUiModel(
        id = "ABC123",
        sellTicker = "RUNE",
        buyTicker = "BTC",
        sellAmount = "125.5",
        targetPrice = "0.00021",
        status = LimitOrderHistoryStatus.Resting,
        createdAt = 0L,
        expiry = UiText.DynamicString("Expires in 11h 32m"),
        fillPercent = 42,
        isCancellable = true,
    )

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewLimitOrderCard() {
    OnBoardingComposeTheme {
        LimitOrderCard(
            item = previewOrder,
            onCancelClick = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewLimitOrderCardCancelling() {
    OnBoardingComposeTheme {
        LimitOrderCard(
            item =
                previewOrder.copy(
                    status = LimitOrderHistoryStatus.Cancelling,
                    isCancellable = false,
                    cancelBlockedReason =
                        UiText.StringResource(R.string.limit_order_cancel_blocked_already_sent),
                ),
            onCancelClick = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
