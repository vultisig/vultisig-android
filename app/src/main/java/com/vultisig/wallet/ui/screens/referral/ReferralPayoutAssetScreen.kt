package com.vultisig.wallet.ui.screens.referral

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
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.models.referral.PayoutAssetUiModel
import com.vultisig.wallet.ui.models.referral.ReferralPayoutAssetUiState
import com.vultisig.wallet.ui.models.referral.ReferralPayoutAssetViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralPayoutAssetScreen(model: ReferralPayoutAssetViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()

    VsModalBottomSheet(
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
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
                )

                VsSearchTextField(fieldState = searchFieldState)
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
                assets.isEmpty() ->
                    Column(modifier = Modifier.padding(all = 16.dp)) {
                        NoFoundContent(message = stringResource(R.string.select_asset_no_result))
                    }
                else ->
                    LazyColumn(contentPadding = PaddingValues(all = 16.dp)) {
                        itemsIndexed(assets, key = { _, item -> item.asset }) { index, item ->
                            val isFirst = index == 0
                            val isLast = index == assets.size - 1
                            val rounding = 12.dp

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
                                UiGradientDivider(
                                    initialColor = Theme.v2.colors.backgrounds.secondary,
                                    endColor = Theme.v2.colors.backgrounds.secondary,
                                )
                            }
                        }
                    }
            }
        },
    )
}

@Composable
private fun PayoutAssetItem(asset: PayoutAssetUiModel, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TokenLogo(
            errorLogoModifier = Modifier.size(32.dp).background(Theme.v2.colors.neutrals.n100),
            logo = asset.logo,
            title = asset.ticker,
            modifier = Modifier.size(32.dp),
        )

        Text(
            text = asset.ticker,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = asset.chain,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
            modifier =
                Modifier.border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = Theme.v2.radius.pill,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )

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
