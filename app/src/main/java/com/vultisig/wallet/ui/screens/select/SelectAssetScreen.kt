package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.VsCenterHighlightCarousel
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.AutoSizingText
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SelectAssetScreen(
    model: SelectAssetViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    VsModalBottomSheet(
        onDismissRequest = model::back,
        content = {
            SelectAssetScreen(
                state = state,
                searchFieldState = model.searchFieldState,
                onAssetClick = model::selectAsset,
                onSelectChain = model::selectChain,
            )
        }
    )
}

@Composable
private fun SelectAssetScreen(
    state: SelectAssetUiModel,
    searchFieldState: TextFieldState,
    onAssetClick: (AssetUiModel) -> Unit,
    onSelectChain: (Chain) -> Unit,
) {

    V2Scaffold(
        applyScaffoldPaddings = true,
        applyDefaultPaddings = false,
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.select_asset_title),
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                )

                VsSearchTextField(
                    fieldState = searchFieldState,
                )
            }
        },
        content = {
            val assets = state.assets
            if (assets.isEmpty()) {
                Column(modifier = Modifier.padding(all = 16.dp)) {
                    NoFoundContent(message = stringResource(R.string.select_asset_no_result))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        all = 16.dp,
                    ),
                ) {
                    itemsIndexed(assets) { index, item ->
                        val isFirst = index == 0
                        val isLast = index == assets.size - 1
                        val rounding = 12.dp

                        AssetItem(
                            logo = item.logo,
                            title = item.title,
                            subtitle = item.subtitle,
                            amount = item.amount,
                            value = item.value,
                            isDisabled = item.isDisabled,
                            modifier = Modifier
                                .clickable(onClick = {
                                    onAssetClick(item)
                                })
                                .background(
                                    color = Theme.v2.colors.backgrounds.secondary,
                                    shape = RoundedCornerShape(
                                        topStart = if (isFirst) rounding else 0.dp,
                                        topEnd = if (isFirst) rounding else 0.dp,
                                        bottomStart = if (isLast) rounding else 0.dp,
                                        bottomEnd = if (isLast) rounding else 0.dp,
                                    )
                                )
                        )

                        if (!isLast) {
                            UiGradientDivider(
                                initialColor = Theme.v2.colors.backgrounds.secondary,
                                endColor = Theme.v2.colors.backgrounds.secondary,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            VsCenterHighlightCarousel(
                onSelectChain = { onSelectChain(it) },
                chains = state.chains,
                selectedChain = state.selectedChain,
            )
        }
    )
}


@Composable
private fun AssetItem(
    logo: ImageModel,
    title: String,
    subtitle: String,
    amount: String,
    value: String,
    isDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(
                horizontal = 20.dp,
                vertical = 12.dp,
            )
    ) {
        TokenLogo(
            errorLogoModifier = Modifier
                .size(32.dp)
                .background(Theme.v2.colors.neutrals.n100),
            logo = logo,
            title = title,
            modifier = Modifier
                .size(32.dp)
        )

        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(2f)
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.light,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(70.dp),
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
        )


        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f),
        ) {
            if (!isDisabled) {
                AutoSizingText(
                    text = amount,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                )

                AutoSizingText(
                    text = value,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.extraLight,
                )
            }
        }

    }
}

@Preview
@Composable
private fun SelectAssetScreenPreview() {
    SelectAssetScreen(
        state = SelectAssetUiModel(
            assets = listOf(
//                AssetUiModel(
//                    token = Coins.Base.WEWE,
//                    logo = "btc",
//                    title = "BTC",
//                    subtitle = "Bitcoin",
//                    amount = "0.00",
//                    value = "$0.00",
//                ),
//                AssetUiModel(
//                    token = Coins.Base.WEWE,
//                    logo = "eth",
//                    title = "ETH",
//                    subtitle = "Ethereum",
//                    amount = "0.00",
//                    value = "$0.00",
//                ),
//                AssetUiModel(
//                    token = Coins.Base.WEWE,
//                    logo = "thor",
//                    title = "LP-GAIA.ATOM/ETH.USDC-XYK",
//                    subtitle = "Thorchain",
//                    amount = "0.00",
//                    value = "$0.00",
//                ),
            )
        ),
        searchFieldState = TextFieldState(),
        onAssetClick = {},
        onSelectChain = {},
    )
}