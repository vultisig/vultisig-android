package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.TransactionHistoryGroupUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryTab
import com.vultisig.wallet.ui.models.TransactionHistoryUiState
import com.vultisig.wallet.ui.models.TransactionHistoryViewModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.theme.Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun TransactionHistoryScreen(viewModel: TransactionHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    TransactionHistoryScreen(
        state = state,
        onBack = viewModel::back,
        onTabSelected = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onItemClick = viewModel::openDetail,
        onDismissDetail = viewModel::dismissDetail,
        onViewExplorer = { url -> if (url.isNotEmpty()) uriHandler.openUri(url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionHistoryScreen(
    state: TransactionHistoryUiState,
    onBack: () -> Unit,
    onTabSelected: (TransactionHistoryTab) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (TransactionHistoryItemUiModel) -> Unit,
    onDismissDetail: () -> Unit = {},
    onViewExplorer: (String) -> Unit = {},
) {
    if (state.selectedItem != null) {
        TransactionDetailBottomSheet(
            item = state.selectedItem,
            onDismiss = onDismissDetail,
            onViewExplorer = onViewExplorer,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.transaction_history_title),
        onBackClick = onBack,
        applyDefaultPaddings = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                VsTabGroup(index = state.selectedTab.ordinal) {
                    tab {
                        VsTab(
                            label = stringResource(R.string.transaction_history_tab_overview),
                            onClick = { onTabSelected(TransactionHistoryTab.OVERVIEW) },
                        )
                    }
                    tab {
                        VsTab(
                            label = stringResource(R.string.transaction_history_tab_swap),
                            onClick = { onTabSelected(TransactionHistoryTab.SWAP) },
                        )
                    }
                    tab {
                        VsTab(
                            label = stringResource(R.string.transaction_history_tab_send),
                            onClick = { onTabSelected(TransactionHistoryTab.SEND) },
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.groups.isEmpty() && !state.isLoading) {
                    TransactionHistoryEmptyState(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                    )
                } else {
                    TransactionGroupedList(
                        groups = state.groups,
                        onItemClick = onItemClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionGroupedList(
    groups: List<TransactionHistoryGroupUiModel>,
    onItemClick: (TransactionHistoryItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            stickyHeader(key = "header_${group.dateLabel}") {
                DateStickyHeader(label = group.dateLabel)
            }
            itemsIndexed(items = group.transactions, key = { _, item -> item.id }) { _, item ->
                when (item) {
                    is TransactionHistoryItemUiModel.Send ->
                        SendTransactionCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) },
                        )
                    is TransactionHistoryItemUiModel.Swap ->
                        SwapTransactionCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) },
                        )
                }
            }
        }
    }
}

@Composable
private fun DateStickyHeader(label: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier =
                Modifier.background(
                        color = Theme.v2.colors.backgrounds.tertiary_2,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

// ── Send card ─────────────────────────────────────────────────────────────────

@Composable
private fun SendTransactionCard(
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
                // ── Header row ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(
                        iconRes = R.drawable.send,
                        label = stringResource(R.string.transaction_history_tab_send),
                    )
                    TransactionStatusWidget(status = item.status)
                }

                UiSpacer(size = 12.dp)

                if (isInProgress) {
                    // ── In-progress layout ───────────────────────────────────────
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
                    // ── Completed layout ─────────────────────────────────────────
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
private fun SendAmountText(amount: String, token: String, modifier: Modifier = Modifier) {
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

@Composable
private fun SendAddressPill(address: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) { append("to ") }
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
                    append("via ")
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

// ── Swap card ─────────────────────────────────────────────────────────────────

@Composable
private fun SwapTransactionCard(
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
            // ── Header row ───────────────────────────────────────────────────
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

            // ── From token ───────────────────────────────────────────────────
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

            // ── To token ─────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TokenCircle(logo = item.toTokenLogo, ticker = item.toToken, size = 32)
                Column {
                    if (isInProgress) {
                        Text(
                            text = stringResource(R.string.transaction_history_min_payout_label),
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    }
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
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun TypeBadge(iconRes: Int, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.info.copy(alpha = 0.6f),
                    shape = CircleShape,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Theme.v2.colors.alerts.info,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.alerts.info,
        )
    }
}

@Composable
private fun TransactionStatusWidget(
    status: TransactionStatusUiModel,
    modifier: Modifier = Modifier,
) {
    val isInProgress =
        status is TransactionStatusUiModel.Broadcasted || status is TransactionStatusUiModel.Pending

    if (isInProgress) {
        Text(
            text = stringResource(R.string.transaction_status_in_progress_label),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
            modifier =
                modifier
                    .background(
                        color = Theme.v2.colors.backgrounds.tertiary_2,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    } else {
        val (label, color) =
            when (status) {
                TransactionStatusUiModel.Confirmed ->
                    stringResource(R.string.transaction_status_confirmed_label) to
                        Theme.v2.colors.alerts.success
                is TransactionStatusUiModel.Failed ->
                    stringResource(R.string.transaction_status_failed_label) to
                        Theme.v2.colors.alerts.error
                else -> "" to Theme.v2.colors.text.tertiary
            }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = Theme.brockmann.supplementary.caption,
                color = color,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ToSeparator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.size(24.dp)
                    .border(1.dp, Theme.v2.colors.border.primaryAccent4, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.info,
                modifier = Modifier.size(12.dp),
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

@Composable
private fun TokenAmountText(amount: String, token: String, modifier: Modifier = Modifier) {
    val numericPart = amount.removeSuffix(token).trim()
    val hasToken = numericPart != amount
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) {
                    append(if (hasToken) numericPart else amount)
                }
                if (hasToken) {
                    append(" ")
                    withStyle(SpanStyle(color = Theme.v2.colors.alerts.success)) { append(token) }
                }
            },
        style = Theme.brockmann.body.m.medium,
        modifier = modifier,
    )
}

@Composable
private fun TokenAmountAnnotated(amount: String, token: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(amount) }
                append(" ")
                withStyle(SpanStyle(color = Theme.v2.colors.alerts.success)) { append(token) }
            },
        style = Theme.brockmann.body.m.medium,
        modifier = modifier,
    )
}

@Composable
private fun TokenCircle(
    modifier: Modifier = Modifier,
    logo: ImageModel,
    ticker: String,
    size: Int = 40,
) {
    Box(
        modifier =
            modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.surface2),
        contentAlignment = Alignment.Center,
    ) {
        TokenLogo(
            modifier = Modifier.size(size.dp),
            errorLogoModifier = Modifier.size(size.dp),
            logo = logo,
            title = ticker,
        )
    }
}

@Composable
private fun TransactionHistoryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_bottom_top),
            contentDescription = null,
            tint = Theme.v2.colors.text.tertiary,
            modifier = Modifier.size(48.dp),
        )
        UiSpacer(size = 12.dp)
        Text(
            text = stringResource(R.string.transaction_history_empty_title),
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.secondary,
        )
        UiSpacer(size = 4.dp)
        Text(
            text = stringResource(R.string.transaction_history_empty_desc),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
    }
}

private fun String.abbreviateAddress(): String {
    if (length <= 12) return this
    return "${take(6)}...${takeLast(4)}"
}

private fun Long.toDetailDateString(): String {
    val formatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}

// ── Transaction Detail Bottom Sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailBottomSheet(
    item: TransactionHistoryItemUiModel,
    onDismiss: () -> Unit,
    onViewExplorer: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier =
                    Modifier.padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            color = Theme.v2.colors.border.light,
                            shape = RoundedCornerShape(100.dp),
                        )
            )
        },
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (item) {
                is TransactionHistoryItemUiModel.Send -> SendDetailContent(item)
                is TransactionHistoryItemUiModel.Swap -> SwapDetailContent(item)
            }

            UiSpacer(size = 24.dp)

            if (item.explorerUrl.isNotEmpty()) {
                VsButton(
                    label = stringResource(R.string.transaction_history_view_on_explorer),
                    variant = VsButtonVariant.Secondary,
                    size = VsButtonSize.Medium,
                    onClick = { onViewExplorer(item.explorerUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SendDetailContent(item: TransactionHistoryItemUiModel.Send) {
    // Type badge
    UiSpacer(size = 8.dp)
    TypeBadge(
        iconRes = R.drawable.send,
        label = stringResource(R.string.transaction_history_tab_send),
    )
    UiSpacer(size = 20.dp)

    // Token hero
    Box(
        modifier =
            Modifier.size(72.dp)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.info.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        TokenCircle(logo = item.tokenLogo, ticker = item.token, size = 48)
    }
    UiSpacer(size = 12.dp)
    TokenAmountText(amount = item.amount, token = item.token)

    UiSpacer(size = 24.dp)

    // Detail rows
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
    // Type badge
    UiSpacer(size = 8.dp)
    TypeBadge(
        iconRes = R.drawable.swap_v2,
        label = stringResource(R.string.transaction_history_tab_swap),
    )
    UiSpacer(size = 20.dp)

    // Token pair hero with cutout shape
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
                modifier = Modifier.weight(1f),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.button.disabled,
            modifier =
                Modifier.size(24.dp)
                    .background(color = Theme.v2.colors.border.light, shape = CircleShape)
                    .padding(6.dp)
                    .align(Alignment.Center),
        )
    }

    UiSpacer(size = 24.dp)

    // Detail rows
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
) {
    Column(
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = shape)
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
                style = Theme.brockmann.supplementary.caption,
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
                .border(1.dp, Theme.v2.colors.border.light, RoundedCornerShape(16.dp))
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
                Text(text = label, style = Theme.brockmann.supplementary.caption, color = color)
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
                    shape = RoundedCornerShape(100.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewSend =
    TransactionHistoryItemUiModel.Send(
        id = "1",
        txHash = "0xabc123",
        chain = "Ethereum",
        status = TransactionStatusUiModel.Confirmed,
        explorerUrl = "",
        timestamp = System.currentTimeMillis(),
        fromAddress = "0x1234567890abcdef",
        toAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        amount = "1,000.12",
        token = "RUNE",
        tokenLogo = "",
        fiatValue = "$1,000.54",
        provider = null,
        feeEstimate = "0.2 ETH",
    )

private val previewSendInProgress =
    TransactionHistoryItemUiModel.Send(
        id = "1b",
        txHash = "0xabc123b",
        chain = "THORChain",
        status = TransactionStatusUiModel.Broadcasted,
        explorerUrl = "",
        timestamp = System.currentTimeMillis(),
        fromAddress = "0x1234567890abcdef",
        toAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        amount = "1,000.12",
        token = "RUNE",
        tokenLogo = "",
        fiatValue = null,
        provider = "THORChain",
        feeEstimate = null,
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
        fromTokenLogo = "",
        toToken = "WBTC",
        toAmount = "0.1251",
        toChain = "Ethereum",
        toTokenLogo = "",
        provider = "Uniswap",
        fiatValue = null,
        fromAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        toAddress = "0xF43jf9840fkfjn38fk0dk9Ac5",
        feeEstimate = "0.2 ETH",
    )

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewOverviewTab() {
    TransactionHistoryScreen(
        state =
            TransactionHistoryUiState(
                selectedTab = TransactionHistoryTab.OVERVIEW,
                groups =
                    listOf(
                        TransactionHistoryGroupUiModel(
                            dateLabel = "Today Sept 2, 2025",
                            transactions = listOf(previewSendInProgress, previewSwap),
                        ),
                        TransactionHistoryGroupUiModel(
                            dateLabel = "Yesterday Sept 1, 2025",
                            transactions =
                                listOf(
                                    previewSend,
                                    previewSend.copy(
                                        id = "3",
                                        token = "SOL",
                                        amount = "200.50",
                                        fiatValue = "$12,204.56",
                                        toAddress = "0xdeadbeefdeadbeef1234",
                                    ),
                                    previewSend.copy(
                                        id = "4",
                                        status =
                                            TransactionStatusUiModel.Failed(reason = "Rejected"),
                                        token = "ETH",
                                        amount = "10.12",
                                        fiatValue = "$321,000.54",
                                        toAddress = "0x0000000000000000abcd",
                                    ),
                                ),
                        ),
                    ),
            ),
        onBack = {},
        onTabSelected = {},
        onRefresh = {},
        onItemClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewSwapTab() {
    TransactionHistoryScreen(
        state =
            TransactionHistoryUiState(
                selectedTab = TransactionHistoryTab.SWAP,
                groups =
                    listOf(
                        TransactionHistoryGroupUiModel(
                            dateLabel = "Today Sept 2, 2025",
                            transactions = listOf(previewSwap),
                        )
                    ),
            ),
        onBack = {},
        onTabSelected = {},
        onRefresh = {},
        onItemClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewEmptyState() {
    TransactionHistoryScreen(
        state =
            TransactionHistoryUiState(
                selectedTab = TransactionHistoryTab.SEND,
                groups = emptyList(),
                isLoading = false,
            ),
        onBack = {},
        onTabSelected = {},
        onRefresh = {},
        onItemClick = {},
    )
}
