package com.vultisig.wallet.ui.screens.transaction.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun TypeBadge(iconRes: Int, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.info,
                    shape = RoundedCornerShape(size = 99.dp),
                )
                .background(
                    color = Theme.v2.colors.alerts.info.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(size = 99.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Theme.v2.colors.alerts.info,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.alerts.info,
        )
    }
}

@Composable
internal fun TransactionStatusWidget(
    status: TransactionStatusUiModel,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is TransactionStatusUiModel.Pending -> {
            // Live counter — matches iOS: "In progress... <elapsedTime>" pill with the elapsed
            // segment in primary text color so the user can see it tick.
            Row(
                modifier =
                    modifier
                        .background(
                            color = Theme.v2.colors.backgrounds.primary,
                            shape = RoundedCornerShape(100.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.transaction_status_in_progress_label) + " ",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = status.elapsedTime.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        TransactionStatusUiModel.Broadcasted ->
            Text(
                text = stringResource(R.string.transaction_status_in_progress_label),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                modifier =
                    modifier
                        .background(
                            color = Theme.v2.colors.backgrounds.primary,
                            shape = RoundedCornerShape(100.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )

        TransactionStatusUiModel.Confirmed ->
            Text(
                text = stringResource(R.string.transaction_status_confirmed_label),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.success,
                modifier = modifier,
            )

        is TransactionStatusUiModel.Failed ->
            Text(
                text = stringResource(R.string.transaction_status_failed_label),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
                modifier = modifier,
            )

        is TransactionStatusUiModel.Refunded ->
            Text(
                text = stringResource(R.string.transaction_status_refunded_label),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.warning,
                modifier = modifier,
            )
    }
}

@Composable
internal fun ToSeparator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_transaction_receive),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.transaction_history_to_label),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Theme.v2.colors.border.light,
            thickness = 1.dp,
        )
    }
}

@Composable
internal fun TokenCircle(
    modifier: Modifier = Modifier,
    logo: ImageModel,
    ticker: String,
    size: Int = 40,
) {
    Box(modifier = modifier.size(size.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        TokenLogo(
            modifier = Modifier.size(size.dp),
            errorLogoModifier = Modifier.size(size.dp),
            logo = logo,
            title = ticker,
        )
    }
}

@Composable
internal fun TokenAmountAnnotated(amount: String, token: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(amount) }
                append(" ")
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(token) }
            },
        style = Theme.brockmann.body.s.medium,
        modifier = modifier,
    )
}

@Composable
internal fun SendAmountText(amount: String, token: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(amount) }
                append(" ")
                withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) { append(token) }
            },
        style = Theme.brockmann.supplementary.footnote,
        modifier = modifier,
    )
}

internal fun String.abbreviateAddress(): String {
    if (length <= 10) return this
    return "${take(6)}...${takeLast(4)}"
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewTransactionStatusWidget() {
    OnBoardingComposeTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransactionStatusWidget(
                status = TransactionStatusUiModel.Pending(elapsedTime = "5s ago".asUi())
            )
            TransactionStatusWidget(
                status = TransactionStatusUiModel.Pending(elapsedTime = "3m ago".asUi())
            )
            TransactionStatusWidget(
                status = TransactionStatusUiModel.Pending(elapsedTime = "2h ago".asUi())
            )
            TransactionStatusWidget(status = TransactionStatusUiModel.Broadcasted)
            TransactionStatusWidget(status = TransactionStatusUiModel.Confirmed)
            TransactionStatusWidget(status = TransactionStatusUiModel.Failed(reason = null))
            TransactionStatusWidget(status = TransactionStatusUiModel.Refunded(reason = null))
        }
    }
}

private fun String.asUi(): UiText = UiText.DynamicString(this)
