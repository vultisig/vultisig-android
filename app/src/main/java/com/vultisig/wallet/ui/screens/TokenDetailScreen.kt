package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.chart.MarketStatsSection
import com.vultisig.wallet.ui.components.chart.PriceChartSection
import com.vultisig.wallet.ui.components.chart.PriceExtremesSection
import com.vultisig.wallet.ui.components.chart.TokenInfoSection
import com.vultisig.wallet.ui.components.v2.bottomsheets.ExpandingBottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChartUiModel
import com.vultisig.wallet.ui.models.MarketStatsUiModel
import com.vultisig.wallet.ui.models.PriceExtremesUiModel
import com.vultisig.wallet.ui.models.TokenDetailUiModel
import com.vultisig.wallet.ui.models.TokenDetailViewModel
import com.vultisig.wallet.ui.models.TokenInfoUiModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainLogo
import com.vultisig.wallet.ui.screens.v2.home.components.AssetAction
import com.vultisig.wallet.ui.screens.v2.home.components.AssetActionItem
import com.vultisig.wallet.ui.screens.v2.home.components.AssetActionRow
import com.vultisig.wallet.ui.screens.v2.home.components.assetActionButtonHeight
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
internal fun TokenDetailScreen(
    viewModel: TokenDetailViewModel = hiltViewModel<TokenDetailViewModel>()
) {
    val uiModel by viewModel.uiState.collectAsState()
    val uriHandler = VsUriHandler()

    TokenDetailScreen(
        uiModel = uiModel,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onDismiss = viewModel::back,
        onBuy = viewModel::buy,
        onReceive = viewModel::receive,
        onExplorer = { uiModel.explorerUrl.takeIf { it.isNotEmpty() }?.let(uriHandler::openUri) },
        onTokenExplorer = {
            uiModel.tokenExplorerUrl.takeIf { it.isNotEmpty() }?.let(uriHandler::openUri)
        },
        onChartRangeSelected = viewModel::onChartRangeSelected,
    )
}

@Composable
internal fun TokenDetailScreen(
    uiModel: TokenDetailUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onBuy: () -> Unit = {},
    onReceive: () -> Unit = {},
    onExplorer: () -> Unit = {},
    onTokenExplorer: () -> Unit = {},
    onChartRangeSelected: (ChartRange) -> Unit = {},
) {
    // The sheet opens on the balance, the actions and the top of the chart, so it is cheap to
    // glance at and swipe away again yet visibly has more below. The rest of the chart and the
    // stats are what scrolling expands the sheet to reach.
    var actionsBottom by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Added here rather than inside the measurement: the chart arrives after the block above it
    // has been measured, and without a size change that measurement is never taken again.
    val chartPeek = if (uiModel.chart != null) ChartGap + ChartPeek else 0.dp
    val restHeight = actionsBottom + with(density) { chartPeek.roundToPx() }

    ExpandingBottomSheet(onDismiss = onDismiss, restHeight = restHeight) {
        TokenDetailsContent(
            uiModel = uiModel,
            onSend = onSend,
            onSwap = onSwap,
            onDeposit = onDeposit,
            onBuy = onBuy,
            onReceive = onReceive,
            onExplorer = onExplorer,
            onTokenExplorer = onTokenExplorer,
            onChartRangeSelected = onChartRangeSelected,
            onActionsBottomMeasured = { actionsBottom = it },
        )
    }
}

@Composable
internal fun TokenDetailsContent(
    uiModel: TokenDetailUiModel,
    onSend: () -> Unit,
    onSwap: () -> Unit,
    onDeposit: () -> Unit,
    onBuy: () -> Unit,
    onReceive: () -> Unit,
    onExplorer: () -> Unit,
    onTokenExplorer: () -> Unit,
    onChartRangeSelected: (ChartRange) -> Unit,
    onActionsBottomMeasured: (Int) -> Unit = {},
) {
    val density = LocalDensity.current

    // No verticalScroll here: the sheet scrolls this content itself, so that a scroll which arrives
    // while it is still resting can be spent on expanding it first.
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            // What the sheet must show before the reader does anything, above the top of the chart
            // when there is one. Its own top padding is added back, since the sheet measures its
            // resting height from its top edge; the sheet fades whatever lies past that height.
            modifier =
                Modifier.fillMaxWidth().onSizeChanged { size ->
                    onActionsBottomMeasured(
                        size.height + with(density) { ContentPadding.roundToPx() }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VsCircleButton(
                onClick = onExplorer,
                size = VsCircleButtonSize.Small,
                icon = R.drawable.explor,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                modifier = Modifier.align(Alignment.End).offset(x = 8.dp, y = (-8).dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ChainLogo(name = uiModel.token.name, logo = uiModel.token.tokenLogo)
                UiSpacer(size = 8.dp)
                Text(
                    text = uiModel.token.name,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                )
            }

            UiSpacer(size = 12.dp)

            LoadableValue(
                value = uiModel.token.fiatBalance,
                isVisible = uiModel.isBalanceVisible,
                style = Theme.satoshi.price.title1,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(size = 12.dp)

            LoadableValue(
                value = uiModel.token.balance,
                isVisible = uiModel.isBalanceVisible,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.text.tertiary,
            )

            UiSpacer(size = 32.dp)

            AssetActionRow(
                actions =
                    buildList {
                        if (uiModel.canSwap) {
                            add(AssetActionItem(action = AssetAction.SWAP, onClick = onSwap))
                        }

                        add(AssetActionItem(action = AssetAction.SEND, onClick = onSend))

                        if (uiModel.canBuy) {
                            add(AssetActionItem(action = AssetAction.BUY, onClick = onBuy))
                        }

                        if (uiModel.canDeposit) {
                            add(
                                AssetActionItem(action = AssetAction.FUNCTIONS, onClick = onDeposit)
                            )
                        }

                        // Gated on the address for the same reason every flag above is gated on
                        // the account: until one resolves there is nothing to put in a QR code,
                        // and an action that is tappable but does nothing is worse than one that
                        // has not appeared yet.
                        if (uiModel.chainAddress.isNotEmpty()) {
                            add(AssetActionItem(action = AssetAction.RECEIVE, onClick = onReceive))
                        }
                    },
                // Send routes from this screen's own arguments and is always available; every
                // other child appears only once the account resolves, so the reserved height keeps
                // the row from growing as they arrive.
                modifier = Modifier.fillMaxWidth().heightIn(min = assetActionButtonHeight),
            )
        }

        UiSpacer(size = ChartGap)

        uiModel.chart?.let { chart ->
            PriceChartSection(
                chart = chart,
                spotPriceText = uiModel.token.price,
                onRangeSelected = onChartRangeSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (uiModel.statsLoading || uiModel.marketStats?.hasAnyValue() == true) {
            if (uiModel.chart != null) UiSpacer(size = 16.dp)
            MarketStatsSection(stats = uiModel.marketStats, isLoading = uiModel.statsLoading)
        }

        if (uiModel.statsLoading || uiModel.priceExtremes?.hasAnyValue() == true) {
            UiSpacer(size = 16.dp)
            PriceExtremesSection(extremes = uiModel.priceExtremes, isLoading = uiModel.statsLoading)
        }

        uiModel.tokenInfo?.let { info ->
            UiSpacer(size = 16.dp)
            TokenInfoSection(
                info = info,
                onExplorer = onTokenExplorer,
                // Pool-priced coins have no chart to headline the price, so it lives here instead.
                price = uiModel.token.price.takeIf { uiModel.chart == null },
            )
        }

        UiSpacer(size = 12.dp)

        // The expanded sheet runs to the bottom of the window, so the last section would otherwise
        // end underneath the gesture bar.
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private val ContentPadding = 24.dp
private val ChartGap = 40.dp

// How far into the chart card the resting sheet reaches: the card's padding and its price header,
// so that the fade falls across the top of the chart line itself. With nothing showing under the
// actions the sheet would not read as scrollable.
private val ChartPeek = 72.dp

@Preview
@Composable
private fun TokenDetailsScreenPreview() {
    TokenDetailScreen(
        uiModel =
            TokenDetailUiModel(
                token =
                    ChainTokenUiModel(
                        name = "USDT",
                        balance = "0.000",
                        fiatBalance = "$0.000000",
                        tokenLogo = R.drawable.usdt,
                        chainLogo = R.drawable.ethereum,
                        price = "$1.00",
                        network = "Ethereum",
                    ),
                canSwap = true,
                canDeposit = true,
                chainAddress = "0xpreview",
                chart = ChartUiModel(),
                marketStats = MarketStatsUiModel(marketCap = "$1.2B", marketCapRank = "#42"),
                priceExtremes =
                    PriceExtremesUiModel(low24h = "$0.98", high24h = "$1.02", bandPosition = 0.4f),
                tokenInfo =
                    TokenInfoUiModel(network = "Ethereum", decimals = "6", hasExplorerLink = true),
            ),
        onSend = {},
        onSwap = {},
        onDeposit = {},
        onDismiss = {},
        onBuy = {},
        onReceive = {},
        onExplorer = {},
        onTokenExplorer = {},
        onChartRangeSelected = {},
    )
}
