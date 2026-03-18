package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.TransactionAssetUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryGroupUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryTab
import com.vultisig.wallet.ui.models.TransactionHistoryUiState
import com.vultisig.wallet.ui.models.TransactionHistoryViewModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

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
        onSearchClick = viewModel::openSearch,
        assetSearchTextFieldState = viewModel.assetSearchTextFieldState,
        onAssetCheckChange = viewModel::toggleAssetSelection,
        onConfirmAssetSearch = viewModel::confirmAssetSearch,
        onDismissAssetSearch = viewModel::closeSearch,
        onRemoveAssetFilter = viewModel::removeAssetFilter,
        onClearAllFilters = viewModel::clearAllFilters,
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
    onSearchClick: () -> Unit = {},
    assetSearchTextFieldState: TextFieldState = rememberTextFieldState(),
    onAssetCheckChange: (TransactionAssetUiModel) -> Unit = {},
    onConfirmAssetSearch: () -> Unit = {},
    onDismissAssetSearch: () -> Unit = {},
    onRemoveAssetFilter: (String) -> Unit = {},
    onClearAllFilters: () -> Unit = {},
) {
    if (state.selectedItem != null) {
        TransactionDetailBottomSheet(
            item = state.selectedItem,
            onDismiss = onDismissDetail,
            onViewExplorer = onViewExplorer,
        )
    }

    if (state.isAssetSearchSheetVisible) {
        AssetSearchBottomSheet(
            items = state.assetSearchItems,
            selectedIds = state.selectedAssetIds,
            searchTextFieldState = assetSearchTextFieldState,
            onCheckChange = onAssetCheckChange,
            onDone = onConfirmAssetSearch,
            onCancel = onDismissAssetSearch,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.transaction_history_title),
        onBackClick = onBack,
        applyDefaultPaddings = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                UiSpacer(weight = 1f)
                V2Container(
                    modifier = Modifier.clickOnce(onClick = onSearchClick),
                    cornerType = CornerType.Circular,
                    type = ContainerType.SECONDARY,
                    borderType = ContainerBorderType.Borderless,
                ) {
                    UiIcon(
                        drawableResId = R.drawable.ic_search,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (state.selectedAssets.isNotEmpty()) {
                SelectedAssetFiltersRow(
                    assets = state.selectedAssets,
                    onRemove = onRemoveAssetFilter,
                    onClearAll = onClearAllFilters,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.groups.isEmpty() && !state.isLoading) {
                    TransactionHistoryEmptyState(
                        modifier = Modifier.fillMaxWidth().padding(top = 27.dp, horizontal = 16.dp)
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
            stickyHeader(key = "header_${group.dateSuffix}") {
                DateStickyHeader(
                    prefix = group.datePrefix.asString(),
                    suffix = group.dateSuffix.asString(),
                )
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
private fun DateStickyHeader(prefix: String?, suffix: String) {
    val primaryColor = Theme.v2.colors.text.primary
    val tertiaryColor = Theme.v2.colors.text.tertiary
    val text = buildAnnotatedString {
        if (!prefix.isNullOrBlank()) {
            withStyle(SpanStyle(color = primaryColor)) { append(prefix) }
            withStyle(SpanStyle(color = tertiaryColor)) { append("  $suffix") }
        } else {
            withStyle(SpanStyle(color = primaryColor)) { append(suffix) }
        }
    }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(vertical = 6.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = Theme.brockmann.supplementary.caption)
    }
}

@Composable
private fun SelectedAssetFiltersRow(
    assets: List<TransactionAssetUiModel>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            assets.forEach { asset ->
                AssetFilterChip(asset = asset, onRemove = { onRemove(asset.tokenId) })
            }
        }
        UiSpacer(size = 8.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = stringResource(R.string.transaction_history_clear_filters),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.alerts.info,
                modifier = Modifier.clickOnce(onClick = onClearAll),
            )
        }
    }
}

@Composable
private fun AssetFilterChip(asset: TransactionAssetUiModel, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Row(
        modifier =
            Modifier.background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.normal, shape = shape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TokenLogo(
            modifier = Modifier.size(16.dp),
            errorLogoModifier = Modifier.size(16.dp),
            logo = asset.logo,
            title = asset.ticker,
        )
        Text(
            text =
                if (asset.chain.isNotEmpty()) "${asset.ticker} (${asset.chain})" else asset.ticker,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )
        UiIcon(
            drawableResId = R.drawable.close_2,
            size = 10.dp,
            tint = Theme.v2.colors.text.tertiary,
            modifier = Modifier.clickOnce(onClick = onRemove),
        )
    }
}

@Composable
private fun AssetSearchBottomSheet(
    items: List<TransactionAssetUiModel>,
    selectedIds: Set<String>,
    searchTextFieldState: TextFieldState,
    onCheckChange: (TransactionAssetUiModel) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    TokenSelectionList(
        items = items.map { GridTokenUiModel.SingleToken(data = it) },
        mapper = {
            TokenSelectionGridUiModel(
                tokenSelectionUiModel =
                    TokenSelectionUiModel.TokenUiSingle(
                        name =
                            if (it.data.chain.isNotEmpty()) "${it.data.ticker} (${it.data.chain})"
                            else it.data.ticker,
                        logo = it.data.logo,
                    ),
                isChecked = it.data.tokenId in selectedIds,
            )
        },
        searchTextFieldState = searchTextFieldState,
        titleContent = {
            Text(
                text = stringResource(R.string.transaction_history_search_asset_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.neutrals.n100,
            )
        },
        notFoundContent = {
            NoFoundContent(
                message = stringResource(R.string.transaction_history_search_asset_not_found)
            )
        },
        onCheckChange = { _, item -> onCheckChange(item) },
        onDoneClick = onDone,
        onCancelClick = onCancel,
    )
}

@Composable
internal fun TransactionHistoryEmptyState(modifier: Modifier = Modifier) {
    val borderBrush =
        Brush.horizontalGradient(
            colorStops =
                arrayOf(
                    0.0f to Theme.v2.colors.backgrounds.surface1,
                    0.495f to Theme.v2.colors.backgrounds.gradientMid,
                    1.0f to Theme.v2.colors.backgrounds.surface1,
                )
        )
    Box(
        modifier =
            modifier.drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = borderBrush,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = Theme.v2.colors.backgrounds.surface1,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_clock),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.info,
                modifier = Modifier.size(24.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.transaction_history_empty_title),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.transaction_history_empty_desc),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
        provider = "Thorchain",
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
                            datePrefix = UiText.DynamicString("Today"),
                            dateSuffix = UiText.DynamicString("Sept 2, 2025"),
                            transactions = listOf(previewSendInProgress, previewSwap),
                        ),
                        TransactionHistoryGroupUiModel(
                            datePrefix = UiText.DynamicString("Yesterday"),
                            dateSuffix = UiText.DynamicString("Sept 1, 2025"),
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
                                            TransactionStatusUiModel.Failed(
                                                reason = UiText.DynamicString("Rejected")
                                            ),
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
                            datePrefix = UiText.DynamicString("Today"),
                            dateSuffix = UiText.DynamicString("Sept 2, 2025"),
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
private fun PreviewWithAssetFilters() {
    TransactionHistoryScreen(
        state =
            TransactionHistoryUiState(
                selectedTab = TransactionHistoryTab.OVERVIEW,
                selectedAssets =
                    listOf(
                        TransactionAssetUiModel(ticker = "ETH", chain = "Ethereum", logo = ""),
                        TransactionAssetUiModel(ticker = "USDC", chain = "Ethereum", logo = ""),
                        TransactionAssetUiModel(ticker = "RUNE", chain = "THORChain", logo = ""),
                        TransactionAssetUiModel(ticker = "WBTC", chain = "Ethereum", logo = ""),
                        TransactionAssetUiModel(ticker = "SOL", chain = "Solana", logo = ""),
                    ),
                selectedAssetIds =
                    setOf(
                        "Ethereum:ETH",
                        "Ethereum:USDC",
                        "THORChain:RUNE",
                        "Ethereum:WBTC",
                        "Solana:SOL",
                    ),
                groups =
                    listOf(
                        TransactionHistoryGroupUiModel(
                            datePrefix = UiText.DynamicString("Today"),
                            dateSuffix = UiText.DynamicString("Sept 2, 2025"),
                            transactions = listOf(previewSend, previewSwap),
                        ),
                        TransactionHistoryGroupUiModel(
                            datePrefix = UiText.DynamicString("Yesterday"),
                            dateSuffix = UiText.DynamicString("Sept 1, 2025"),
                            transactions = listOf(previewSendInProgress),
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
