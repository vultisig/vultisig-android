package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.chart.MarketStatsSection
import com.vultisig.wallet.ui.components.chart.PriceChartSection
import com.vultisig.wallet.ui.components.chart.PriceExtremesSection
import com.vultisig.wallet.ui.components.chart.TokenInfoSection
import com.vultisig.wallet.ui.components.v2.bottomsheets.DottyBottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenMetaRow
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
        onExplorer = { uiModel.explorerUrl.takeIf { it.isNotEmpty() }?.let(uriHandler::openUri) },
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
    onExplorer: () -> Unit = {},
    onChartRangeSelected: (ChartRange) -> Unit = {},
) {
    // Partial expansion is what makes this sheet cheap to scan and swipe away again. The rest
    // position is half the screen for every token rather than a function of how much content it
    // has — the same trade iOS makes with a .medium detent — so a token with no chart data rests
    // there too and drags up for the rest.
    DottyBottomSheet(onDismiss = onDismiss, skipPartiallyExpanded = false) {
        TokenDetailsContent(
            uiModel = uiModel,
            onSend = onSend,
            onSwap = onSwap,
            onDeposit = onDeposit,
            onBuy = onBuy,
            onExplorer = onExplorer,
            onChartRangeSelected = onChartRangeSelected,
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
    onExplorer: () -> Unit,
    onChartRangeSelected: (ChartRange) -> Unit,
) {
    // fillMaxWidth, not fillMaxSize: the sheet derives its anchors from the content's height, so
    // filling the height here would pin every token to a half-height rest position even when there
    // is nothing below the fold to scroll to.
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
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
            // Every flag below starts false and only resolves once the account loads, so without a
            // reserved height this row is zero-height on the first frame and the content jumps
            // once the buttons appear.
            modifier = Modifier.fillMaxWidth().heightIn(min = assetActionButtonHeight),
        ) {
            if (uiModel.canSwap) {
                AssetActionButton(action = AssetAction.SWAP, isSelected = true, onClick = onSwap)
            }

            if (uiModel.canSend) {
                AssetActionButton(action = AssetAction.SEND, isSelected = false, onClick = onSend)
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
        }

        UiSpacer(size = 40.dp)

        TopShineContainer(backgroundColor = Theme.v2.colors.backgrounds.surface1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TokenMetaRow(
                    key = stringResource(R.string.token_details_bottom_sheet_price),
                    value = uiModel.token.price,
                )
                UiHorizontalDivider(modifier = Modifier.fillMaxWidth())
                TokenMetaRow(
                    key = stringResource(R.string.token_details_bottom_sheet_network),
                    value = uiModel.token.network,
                )
            }
        }

        uiModel.chart?.let { chart ->
            UiSpacer(size = 24.dp)
            PriceChartSection(
                chart = chart,
                onRangeSelected = onChartRangeSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (uiModel.statsLoading || uiModel.marketStats?.hasAnyValue() == true) {
            UiSpacer(size = 24.dp)
            MarketStatsSection(stats = uiModel.marketStats, isLoading = uiModel.statsLoading)
        }

        if (uiModel.statsLoading || uiModel.priceExtremes?.hasAnyValue() == true) {
            UiSpacer(size = 24.dp)
            PriceExtremesSection(extremes = uiModel.priceExtremes, isLoading = uiModel.statsLoading)
        }

        uiModel.tokenInfo?.let { info ->
            UiSpacer(size = 24.dp)
            TokenInfoSection(info = info)
        }

        UiSpacer(size = 12.dp)
    }
}

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
                priceExtremes = PriceExtremesUiModel(low24h = "$0.98", high24h = "$1.02"),
                tokenInfo = TokenInfoUiModel(decimals = "6"),
            ),
        onSend = {},
        onSwap = {},
        onDeposit = {},
        onDismiss = {},
        onBuy = {},
        onExplorer = {},
        onChartRangeSelected = {},
    )
}
