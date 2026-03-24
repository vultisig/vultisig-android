package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.screens.transaction.components.TokenAmountAnnotated
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.screens.transaction.components.TypeBadge
import com.vultisig.wallet.ui.screens.transaction.components.abbreviateAddress
import com.vultisig.wallet.ui.theme.Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailBottomSheet(
    item: TransactionHistoryItemUiModel,
    onDismiss: () -> Unit,
    onViewExplorer: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {},
    ) {
        Box(
            modifier =
                Modifier.shadow(
                        elevation = 75.dp,
                        spotColor = Color(0x2E000000),
                        ambientColor = Color(0x2E000000),
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                    )
                    .background(Theme.v2.colors.variables.backgroundsSurface1)
        ) {
            Image(
                painter = painterResource(R.drawable.magic_pattern),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UiSpacer(size = 8.dp)
                Box(
                    modifier =
                        Modifier.graphicsLayer { blendMode = BlendMode.Plus }
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                color = Theme.v2.colors.vibrant.primary,
                                shape = RoundedCornerShape(100.dp),
                            )
                )

                when (item) {
                    is TransactionHistoryItemUiModel.Send -> SendDetailContent(item)
                    is TransactionHistoryItemUiModel.Swap -> SwapDetailContent(item)
                }

                UiSpacer(size = 24.dp)

                if (item.explorerUrl.isNotEmpty()) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.clickOnce(onClick = { onViewExplorer(item.explorerUrl) })
                                .border(
                                    width = 1.dp,
                                    color = Color(0x08FFFFFF),
                                    shape = RoundedCornerShape(size = 99.dp),
                                )
                                .background(
                                    color = Theme.v2.colors.variables.bordersLight,
                                    shape = RoundedCornerShape(size = 99.dp),
                                )
                                .padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.transaction_history_view_on_explorer),
                            style = Theme.brockmann.button.medium.medium,
                            color = Theme.v2.colors.variables.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendDetailContent(item: TransactionHistoryItemUiModel.Send) {
    UiSpacer(size = 33.dp)
    TypeBadge(
        iconRes = R.drawable.send_2,
        label = stringResource(R.string.transaction_history_tab_send),
    )
    UiSpacer(size = 16.dp)

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(size = 16.dp),
                )
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.surface2,
                    shape = RoundedCornerShape(size = 16.dp),
                )
                .padding(vertical = 16.dp, horizontal = 20.dp),
    ) {
        TokenCircle(logo = item.tokenLogo, ticker = item.token, size = 48)
        UiSpacer(size = 12.dp)
        TokenAmountText(amount = item.amount, token = item.token)
    }

    UiSpacer(size = 40.dp)

    DetailInfoRows(
        status = item.status,
        fromAddress = item.fromAddress.takeIf { it.isNotEmpty() },
        toAddress = item.toAddress.takeIf { it.isNotEmpty() },
        timestamp = item.timestamp,
        feeEstimate = item.feeEstimate,
        network = item.chain,
        provider = null,
    )
}

@Composable
private fun SwapDetailContent(item: TransactionHistoryItemUiModel.Swap) {
    UiSpacer(size = 33.dp)
    TypeBadge(
        iconRes = R.drawable.swap,
        label = stringResource(R.string.transaction_history_tab_swap),
    )
    UiSpacer(size = 16.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwapTokenCard(
                logo = item.fromTokenLogo,
                ticker = item.fromToken,
                amount = item.fromAmount,
                fiatValue = item.fiatValue,
                shape =
                    RoundedWithCutoutShape(
                        cutoutPosition = CutoutPosition.End,
                        cutoutOffsetX = (-4).dp,
                        cutoutRadius = 18.dp,
                    ),
                color = Theme.v2.colors.backgrounds.surface2,
                modifier = Modifier.weight(1f),
            )
            SwapTokenCard(
                logo = item.toTokenLogo,
                ticker = item.toToken,
                amount = item.toAmount,
                fiatValue = item.fiatValue,
                shape =
                    RoundedWithCutoutShape(
                        cutoutPosition = CutoutPosition.Start,
                        cutoutOffsetX = (-4).dp,
                        cutoutRadius = 18.dp,
                    ),
                color = Theme.v2.colors.backgrounds.surface2,
                modifier = Modifier.weight(1f),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.button.disabled,
            modifier =
                Modifier.size(24.dp)
                    .background(color = Theme.v2.colors.variables.bordersLight, shape = CircleShape)
                    .padding(6.dp)
                    .align(Alignment.Center),
        )
    }

    UiSpacer(size = 40.dp)

    DetailInfoRows(
        status = item.status,
        fromAddress = item.fromAddress,
        toAddress = item.toAddress,
        timestamp = item.timestamp,
        feeEstimate = item.feeEstimate,
        network = item.chain,
        provider = item.provider.takeIf { it.isNotEmpty() },
    )
}

@Composable
private fun SwapTokenCard(
    logo: ImageModel,
    ticker: String,
    amount: String,
    fiatValue: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = Theme.v2.colors.backgrounds.secondary,
) {
    Column(
        modifier =
            modifier
                .background(color = color, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TokenCircle(logo = logo, ticker = ticker, size = 32)
        TokenAmountAnnotated(amount = amount, token = ticker)
        if (!fiatValue.isNullOrEmpty()) {
            Text(
                text = fiatValue,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

@Composable
private fun DetailInfoRows(
    status: TransactionStatusUiModel,
    fromAddress: String?,
    toAddress: String?,
    timestamp: Long,
    feeEstimate: String?,
    network: String,
    provider: String?,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .border(1.dp, Theme.v2.colors.border.light, RoundedCornerShape(12.dp))
                .background(
                    color = Theme.v2.colors.variables.backgroundsSurface1,
                    shape = RoundedCornerShape(size = 12.dp),
                )
                .padding(horizontal = 4.dp)
    ) {
        DetailRow(
            label = stringResource(R.string.transaction_history_detail_status),
            value = {
                val (label, color) =
                    when (status) {
                        TransactionStatusUiModel.Confirmed ->
                            stringResource(R.string.transaction_status_confirmed_label) to
                                Theme.v2.colors.alerts.success

                        is TransactionStatusUiModel.Failed ->
                            stringResource(R.string.transaction_status_failed_label) to
                                Theme.v2.colors.alerts.error

                        TransactionStatusUiModel.Broadcasted ->
                            stringResource(R.string.transaction_status_in_progress_label) to
                                Theme.v2.colors.text.secondary

                        is TransactionStatusUiModel.Pending ->
                            stringResource(R.string.transaction_status_in_progress_label) to
                                Theme.v2.colors.text.secondary
                    }
                Row(
                    modifier =
                        Modifier.background(
                                color = Theme.v2.colors.variables.backgroundsSurface12,
                                shape = RoundedCornerShape(size = 8.dp),
                            )
                            .padding(vertical = 3.dp, horizontal = 8.dp)
                ) {
                    Text(text = label, style = Theme.brockmann.supplementary.caption, color = color)
                }
            },
        )
        if (!fromAddress.isNullOrEmpty()) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
            DetailRow(
                label = stringResource(R.string.transaction_history_detail_from),
                value = { DetailValuePill(text = fromAddress.abbreviateAddress()) },
            )
        }
        if (!toAddress.isNullOrEmpty()) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
            DetailRow(
                label = stringResource(R.string.transaction_history_detail_to),
                value = { DetailValuePill(text = toAddress.abbreviateAddress()) },
            )
        }
        HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
        DetailRow(
            label = stringResource(R.string.transaction_history_detail_date),
            value = { DetailValuePill(text = timestamp.toDetailDateString()) },
        )
        if (!provider.isNullOrEmpty()) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
            DetailRow(
                label = stringResource(R.string.transaction_history_detail_provider),
                value = { DetailValuePill(text = provider) },
            )
        }
        if (!feeEstimate.isNullOrEmpty()) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
            DetailRow(
                label = stringResource(R.string.transaction_history_detail_fee),
                value = { DetailValuePill(text = feeEstimate) },
            )
        }
        HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
        DetailRow(
            label = stringResource(R.string.transaction_history_detail_network),
            value = { DetailValuePill(text = network) },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.secondary,
        )
        value()
    }
}

@Composable
private fun DetailValuePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.primary,
        modifier =
            modifier
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TokenAmountText(amount: String, token: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(amount) }
                append(" ")
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(token) }
            },
        style = Theme.satoshi.price.title2,
        modifier = modifier,
    )
}

private fun Long.toDetailDateString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}

private val previewSend =
    TransactionHistoryItemUiModel.Send(
        id = "1",
        txHash = "0xabc123",
        chain = "ETH",
        status = TransactionStatusUiModel.Confirmed,
        explorerUrl = "",
        timestamp = System.currentTimeMillis(),
        fromAddress = "0x1234567890abcdef",
        toAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        amount = "1,000.12",
        token = "RUNE",
        tokenLogo = R.drawable.ethereum,
        fiatValue = "$1,000.54",
        provider = null,
        feeEstimate = "0.2 ETH",
    )

private val previewSwap =
    TransactionHistoryItemUiModel.Swap(
        id = "2",
        txHash = "0xdef456",
        chain = "Ethereum",
        status = TransactionStatusUiModel.Broadcasted,
        explorerUrl = "",
        timestamp = System.currentTimeMillis() - 3 * 60_000,
        fromToken = "RUNE",
        fromAmount = "1,000.12",
        fromChain = "THORChain",
        fromTokenLogo = R.drawable.rune,
        toToken = "WBTC",
        toAmount = "0.1251",
        toChain = "Ethereum",
        toTokenLogo = R.drawable.wbtc,
        provider = "Thorchain",
        providerLogo = null,
        fiatValue = null,
        fromAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        toAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        feeEstimate = "0.2 ETH",
    )

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewTransactionDetailBottomSheetSend() {
    TransactionDetailBottomSheet(item = previewSend, onDismiss = {}, onViewExplorer = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewTransactionDetailBottomSheetSwap() {
    TransactionDetailBottomSheet(item = previewSwap, onDismiss = {}, onViewExplorer = {})
}
