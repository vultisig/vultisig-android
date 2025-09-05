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
import androidx.compose.material3.Scaffold
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
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.VsCenterHighlightCarousel
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
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
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.select_asset_title),
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.colors.text.primary,
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
        content = { contentPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    all = 16.dp,
                ),
                modifier = Modifier
                    .padding(contentPadding),
            ) {
                val assets = state.assets
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
                                color = Theme.colors.backgrounds.secondary,
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
                            initialColor = Theme.colors.backgrounds.secondary,
                            endColor = Theme.colors.backgrounds.secondary,
                        )
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
                .background(Theme.colors.neutral100),
            logo = logo,
            title = title,
            modifier = Modifier
                .size(32.dp)
        )

        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.light,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
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
                Text(
                    text = amount,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.text.primary,
                )

                Text(
                    text = value,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.extraLight,
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
                AssetUiModel(
                    token = Tokens.wewe,
                    logo = "btc",
                    title = "BTC",
                    subtitle = "Bitcoin",
                    amount = "0.00",
                    value = "$0.00",
                ),
                AssetUiModel(
                    token = Tokens.wewe,
                    logo = "eth",
                    title = "ETH",
                    subtitle = "Ethereum",
                    amount = "0.00",
                    value = "$0.00",
                ),
            )
        ),
        searchFieldState = TextFieldState(),
        onAssetClick = {},
        onSelectChain = {},
    )
}