package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.bottomsheet.SettingsModalBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.models.referral.PayoutAssetUiModel
import com.vultisig.wallet.ui.models.referral.ReferralPayoutAssetUiState
import com.vultisig.wallet.ui.models.referral.ReferralPayoutAssetViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralPayoutAssetScreen(model: ReferralPayoutAssetViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()

    SettingsModalBottomSheet(
        onDismissRequest = model::back,
        content = {
            ReferralPayoutAssetScreen(
                state = state,
                searchFieldState = model.searchFieldState,
                onAssetClick = model::onAssetClick,
            )
        },
    )
}

@Composable
private fun ReferralPayoutAssetScreen(
    state: ReferralPayoutAssetUiState,
    searchFieldState: TextFieldState,
    onAssetClick: (PayoutAssetUiModel) -> Unit,
) {
    V2Scaffold(
        applyScaffoldPaddings = true,
        applyDefaultPaddings = false,
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.select_asset_title),
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(all = 10.dp),
                )

                SearchBar(
                    state = searchFieldState,
                    isInitiallyFocused = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        content = {
            val assets = state.assets
            when {
                state.isLoading ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(all = 32.dp),
                    ) {
                        VsCircularLoading(modifier = Modifier.size(32.dp))
                    }
                state.isError ->
                    Column(modifier = Modifier.padding(all = 16.dp)) {
                        NoFoundContent(message = stringResource(R.string.error_loading_information))
                    }
                assets.isEmpty() ->
                    Column(modifier = Modifier.padding(all = 16.dp)) {
                        NoFoundContent(message = stringResource(R.string.select_asset_no_result))
                    }
                else ->
                    PayoutAssetList(assets = assets, onAssetClick = onAssetClick)
            }
        },
    )
}

@Composable
private fun PayoutAssetList(
    assets: List<PayoutAssetUiModel>,
    onAssetClick: (PayoutAssetUiModel) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(assets, key = { _, item -> item.asset }) { index, item ->
            val isFirst = index == 0
            val isLast = index == assets.size - 1
            val rounding = Theme.v2.radius.xl.size

            PayoutAssetItem(
                asset = item,
                modifier =
                    Modifier.clickable(onClick = { onAssetClick(item) })
                        .background(
                            color = Theme.v2.colors.backgrounds.secondary,
                            shape =
                                RoundedCornerShape(
                                    topStart = if (isFirst) rounding else 0.dp,
                                    topEnd = if (isFirst) rounding else 0.dp,
                                    bottomStart = if (isLast) rounding else 0.dp,
                                    bottomEnd = if (isLast) rounding else 0.dp,
                                ),
                        ),
            )

            if (!isLast) {
                UiHorizontalDivider()
            }
        }
    }
}

@Composable
private fun PayoutAssetItem(asset: PayoutAssetUiModel, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        TokenLogo(
            errorLogoModifier = Modifier.size(32.dp).background(Theme.v2.colors.neutrals.n100),
            logo = asset.logo,
            title = asset.ticker,
            modifier = Modifier.size(32.dp),
        )

        UiSpacer(12.dp)

        Text(
            text = asset.ticker,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(8.dp)

        Text(
            text = asset.chain,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.v2.colors.text.secondary,
            modifier =
                Modifier.background(
                        color = Theme.v2.colors.backgrounds.secondary,
                        shape = Theme.v2.radius.pill,
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = Theme.v2.radius.pill,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        UiSpacer(1f)

        if (asset.isSelected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.success,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ReferralPayoutAssetScreenPreview() {
    ReferralPayoutAssetScreen(
        state =
            ReferralPayoutAssetUiState(
                isLoading = false,
                assets =
                    listOf(
                        PayoutAssetUiModel(
                            asset = "BTC.BTC",
                            logo = "btc",
                            ticker = "BTC",
                            chain = "Bitcoin",
                        ),
                        PayoutAssetUiModel(
                            asset = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
                            logo = "usdc",
                            ticker = "USDC",
                            chain = "Ethereum",
                            isSelected = true,
                        ),
                    ),
            ),
        searchFieldState = TextFieldState(),
        onAssetClick = {},
    )
}
