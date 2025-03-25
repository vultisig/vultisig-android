@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SelectNetworkScreen(
    model: SelectNetworkViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    VsModalBottomSheet(
        onDismissRequest = model::back,
        content = {
            SelectNetworkScreen(
                state = state,
                searchFieldState = model.searchFieldState,
                onNetworkClick = model::selectNetwork,
            )
        }
    )
}

@Composable
private fun SelectNetworkScreen(
    state: SelectNetworkUiModel,
    searchFieldState: TextFieldState,
    onNetworkClick: (NetworkUiModel) -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.select_network_title),
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
                item {
                    Text(
                        text = stringResource(R.string.select_network_network_title),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.colors.text.extraLight,
                    )

                    UiSpacer(16.dp)
                }

                val networks = state.networks
                itemsIndexed(networks) { index, item ->
                    val isFirst = index == 0
                    val isLast = index == networks.size - 1
                    val rounding = 12.dp
                    val isSelected = state.selectedNetwork == item.chain

                    NetworkItem(
                        logo = item.logo,
                        title = item.title,
                        isSelected = isSelected,
                        modifier = Modifier
                            .clickable(onClick = {
                                onNetworkClick(item)
                            })
                            .background(
                                color = if (isSelected)
                                    Theme.colors.backgrounds.tertiary
                                else Theme.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(
                                    topStart = if (isFirst) rounding else 0.dp,
                                    topEnd = if (isFirst) rounding else 0.dp,
                                    bottomStart = if (isLast) rounding else 0.dp,
                                    bottomEnd = if (isLast) rounding else 0.dp,
                                )
                            )
                    )

                    if (!isLast) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Theme.colors.borders.light,
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun NetworkItem(
    logo: ImageModel,
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
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
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
            modifier = Modifier
                .weight(1f)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .background(
                        color = Theme.colors.backgrounds.secondary,
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Theme.colors.alerts.success,
                    modifier = Modifier
                        .size(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SelectNetworkScreenPreview() {
    SelectNetworkScreen(
        state = SelectNetworkUiModel(
            selectedNetwork = Chain.Bitcoin,
            networks = listOf(
                NetworkUiModel(
                    chain = Chain.Bitcoin,
                    logo = "btc",
                    title = "BTC",
                ),
                NetworkUiModel(
                    chain = Chain.Ethereum,
                    logo = "eth",
                    title = "ETH",
                ),
            )
        ),
        searchFieldState = TextFieldState(),
        onNetworkClick = {},
    )
}