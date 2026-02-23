package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
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


@Composable
internal fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TransactionHistoryScreen(
        state = state,
        onBack = viewModel::back,
        onTabSelected = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onItemClick = {},
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
) {
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
                            label = stringResource(R.string.transaction_history_tab_send),
                            onClick = { onTabSelected(TransactionHistoryTab.SEND) },
                        )
                    }
                    tab {
                        VsTab(
                            label = stringResource(R.string.transaction_history_tab_swap),
                            onClick = { onTabSelected(TransactionHistoryTab.SWAP) },
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
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
    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            stickyHeader(key = "header_${group.dateLabel}") {
                DateStickyHeader(label = group.dateLabel)
            }
            itemsIndexed(
                items = group.transactions,
                key = { _, item -> item.id },
            ) { index, item ->
                Column {
                    when (item) {
                        is TransactionHistoryItemUiModel.Send -> SendTransactionItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                        is TransactionHistoryItemUiModel.Swap -> SwapTransactionItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                    if (index != group.transactions.lastIndex) {
                        UiHorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStickyHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

// ── List items ────────────────────────────────────────────────────────────────

@Composable
private fun SendTransactionItem(
    item: TransactionHistoryItemUiModel.Send,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TokenCircle(logoUrl = item.tokenLogo, ticker = item.token)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${item.token} · ${item.chain}",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                UiSpacer(size = 8.dp)
                Text(
                    text = item.amount,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                )
            }

            UiSpacer(size = 4.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransactionStatusBadge(status = item.status)
                UiSpacer(size = 8.dp)
                Text(
                    text = item.toAddress.abbreviateAddress(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                    maxLines = 1,
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.tertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SwapTransactionItem(
    item: TransactionHistoryItemUiModel.Swap,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwapTokenCircles(
            fromLogoUrl = item.fromTokenLogo,
            toLogoUrl = item.toTokenLogo,
            fromTicker = item.fromToken,
            toTicker = item.toToken,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${item.fromToken} → ${item.toToken}",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                UiSpacer(size = 8.dp)
                if (item.fiatValue != null) {
                    Text(
                        text = item.fiatValue,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                        maxLines = 1,
                    )
                }
            }

            UiSpacer(size = 4.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransactionStatusBadge(status = item.status)
                UiSpacer(size = 8.dp)
                Text(
                    text = "${item.fromAmount} → ${item.toAmount}",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.tertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}


@Composable
private fun TransactionStatusBadge(
    status: TransactionStatusUiModel,
    modifier: Modifier = Modifier,
) {
    val (label, tint) = when (status) {
        TransactionStatusUiModel.Broadcasted -> Pair(
            stringResource(R.string.transaction_status_broadcasted_label),
            Theme.v2.colors.alerts.info,
        )
        is TransactionStatusUiModel.Pending -> Pair(
            stringResource(R.string.transaction_status_pending_label, status.elapsedTime),
            Theme.v2.colors.alerts.warning,
        )
        TransactionStatusUiModel.Confirmed -> Pair(
            stringResource(R.string.transaction_status_confirmed_label),
            Theme.v2.colors.alerts.success,
        )
        is TransactionStatusUiModel.Failed -> Pair(
            stringResource(R.string.transaction_status_failed_label),
            Theme.v2.colors.alerts.error,
        )
    }

    Text(
        text = label,
        style = Theme.brockmann.supplementary.caption,
        color = tint,
        modifier = modifier
            .background(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}


@Composable
private fun TokenCircle(
    logoUrl: String,
    ticker: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Theme.v2.colors.backgrounds.secondary),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNotEmpty()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = ticker,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = ticker.take(1).uppercase(),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun SwapTokenCircles(
    fromLogoUrl: String,
    toLogoUrl: String,
    fromTicker: String,
    toTicker: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 54.dp, height = 36.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.secondary)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            if (fromLogoUrl.isNotEmpty()) {
                AsyncImage(
                    model = fromLogoUrl,
                    contentDescription = fromTicker,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = fromTicker.take(1).uppercase(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp + 3.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.primary)
                .align(Alignment.CenterEnd),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.secondary)
                .align(Alignment.CenterEnd)
                .offset(x = (-1.5).dp),
            contentAlignment = Alignment.Center,
        ) {
            if (toLogoUrl.isNotEmpty()) {
                AsyncImage(
                    model = toLogoUrl,
                    contentDescription = toTicker,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = toTicker.take(1).uppercase(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }
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


private val previewSend = TransactionHistoryItemUiModel.Send(
    id = "1",
    txHash = "0xabc123",
    chain = "Ethereum",
    status = TransactionStatusUiModel.Confirmed,
    explorerUrl = "",
    timestamp = System.currentTimeMillis(),
    fromAddress = "0x1234567890abcdef",
    toAddress = "0xdeadbeefdeadbeef1234",
    amount = "0.5 ETH",
    token = "ETH",
    tokenLogo = "",
    fiatValue = "$1,200.00",
)

private val previewSwap = TransactionHistoryItemUiModel.Swap(
    id = "2",
    txHash = "0xdef456",
    chain = "Ethereum",
    status = TransactionStatusUiModel.Pending(elapsedTime = "3m ago"),
    explorerUrl = "",
    timestamp = System.currentTimeMillis() - 3 * 60_000,
    fromToken = "ETH",
    fromAmount = "0.5",
    fromChain = "Ethereum",
    fromTokenLogo = "",
    toToken = "USDC",
    toAmount = "1,200.00",
    toChain = "Ethereum",
    toTokenLogo = "",
    provider = "THORChain",
    fiatValue = "$1,200.00",
)

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun PreviewOverviewTab() {
    TransactionHistoryScreen(
        state = TransactionHistoryUiState(
            selectedTab = TransactionHistoryTab.OVERVIEW,
            groups = listOf(
                TransactionHistoryGroupUiModel(
                    dateLabel = "Today",
                    transactions = listOf(
                        previewSend,
                        previewSwap,
                    ),
                ),
                TransactionHistoryGroupUiModel(
                    dateLabel = "Yesterday",
                    transactions = listOf(
                        previewSend.copy(
                            id = "3",
                            status = TransactionStatusUiModel.Failed(reason = "Insufficient funds"),
                            amount = "1.2 ETH",
                            toAddress = "0xabababababababab9999",
                        ),
                        previewSwap.copy(
                            id = "4",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSend.copy(
                            id = "5",
                            status = TransactionStatusUiModel.Failed(reason = "Insufficient funds"),
                            amount = "1.2 ETH",
                            toAddress = "0xabababababababab9999",
                        ),
                        previewSwap.copy(
                            id = "6",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSend.copy(
                            id = "7",
                            status = TransactionStatusUiModel.Failed(reason = "Insufficient funds"),
                            amount = "1.2 ETH",
                            toAddress = "0xabababababababab9999",
                        ),
                        previewSwap.copy(
                            id = "8",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSend.copy(
                            id = "9",
                            status = TransactionStatusUiModel.Failed(reason = "Insufficient funds"),
                            amount = "1.2 ETH",
                            toAddress = "0xabababababababab9999",
                        ),
                        previewSwap.copy(
                            id = "40",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSwap.copy(
                            id = "41",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSwap.copy(
                            id = "42",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
                        ),
                        previewSwap.copy(
                            id = "43",
                            status = TransactionStatusUiModel.Broadcasted,
                            fromToken = "BTC",
                            toToken = "ETH",
                            fromAmount = "0.01",
                            toAmount = "0.32",
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
private fun PreviewSendTab() {
    TransactionHistoryScreen(
        state = TransactionHistoryUiState(
            selectedTab = TransactionHistoryTab.SEND,
            groups = listOf(
                TransactionHistoryGroupUiModel(
                    dateLabel = "Today",
                    transactions = listOf(
                        previewSend,
                        previewSend.copy(
                            id = "5",
                            status = TransactionStatusUiModel.Pending(elapsedTime = "12m ago"),
                            amount = "100 USDC",
                            token = "USDC",
                            toAddress = "0x9999999999999999abcd",
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
private fun PreviewEmptyState() {
    TransactionHistoryScreen(
        state = TransactionHistoryUiState(
            selectedTab = TransactionHistoryTab.SWAP,
            groups = emptyList(),
            isLoading = false,
        ),
        onBack = {},
        onTabSelected = {},
        onRefresh = {},
        onItemClick = {},
    )
}