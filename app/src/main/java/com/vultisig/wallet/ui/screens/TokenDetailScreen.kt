package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.vultisig.wallet.ui.screens.v2.home.components.AssetActionButton
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
    // The sheet opens on the balance and the actions and nothing else, which is what keeps it to
    // the third of the screen the design asks for and cheap to glance at and swipe away again.
    // Everything below that fold — the price row, the chart, the stats — is what scrolling expands
    // the sheet to reach.
    var restHeight by remember { mutableIntStateOf(0) }

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
            onRestHeightMeasured = { restHeight = it },
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
    onRestHeightMeasured: (Int) -> Unit = {},
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
            // What the sheet must show before the reader does anything. Its own top padding is
            // added back, since the sheet measures its resting height from its top edge; the sheet
            // keeps this block clear of the fade at its bottom edge on its own.
            modifier =
                Modifier.fillMaxWidth().onSizeChanged { size ->
                    onRestHeightMeasured(size.height + with(density) { ContentPadding.roundToPx() })
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

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(space = 20.dp, alignment = Alignment.CenterHorizontally),
                // Every flag below starts false and only resolves once the account loads, so
                // without a reserved height this row is zero-height on the first frame and the
                // content jumps once the buttons appear.
                modifier = Modifier.fillMaxWidth().heightIn(min = assetActionButtonHeight),
            ) {
                if (uiModel.canSwap) {
                    AssetActionButton(
                        action = AssetAction.SWAP,
                        isSelected = true,
                        onClick = onSwap,
                    )
                }

                if (uiModel.canSend) {
                    AssetActionButton(
                        action = AssetAction.SEND,
                        isSelected = false,
                        onClick = onSend,
                    )
                }

                if (uiModel.canBuy) {
                    AssetActionButton(action = AssetAction.BUY, isSelected = false, onClick = onBuy)
                }

                if (uiModel.canDeposit) {
                    AssetActionButton(
                        action = AssetAction.FUNCTIONS,
                        isSelected = false,
                        onClick = onDeposit,
                    )
                }

                AssetActionButton(
                    action = AssetAction.RECEIVE,
                    isSelected = false,
                    onClick = onReceive,
                )
            }
        }

        UiSpacer(size = 40.dp)

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
