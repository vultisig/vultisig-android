package com.vultisig.wallet.ui.screens.v2.receive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.models.ChainToReceiveUiModel
import com.vultisig.wallet.ui.models.ReceiveUiModel
import com.vultisig.wallet.ui.models.ReceiveViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReceiveBottomSheet(
    viewModel: ReceiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    V2BottomSheet(
        onDismissRequest = viewModel::back
    ) {
        ReceiveContent(
            searchFieldState = viewModel.searchFieldState,
            uiState = uiState,
            onChainClick = viewModel::onChainClick
        )

    }

}

@Composable
private fun ReceiveContent(
    uiState: ReceiveUiModel,
    searchFieldState: TextFieldState,
    onChainClick: (ChainToReceiveUiModel) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp
                )
    ) {
        UiSpacer(
            size = 32.dp
        )
        Text(
            text = stringResource(R.string.select_chain_title),
            style = Theme.brockmann.body.l.medium,
            color = Theme.v2.colors.text.primary,
        )
        UiSpacer(16.dp)

        SearchBar(
            isInitiallyFocused = false,
            state = searchFieldState,
            onCancelClick = {},
        )

        UiSpacer(
            size = 16.dp
        )

        V2Container(
            type = ContainerType.SECONDARY
        ) {
            LazyColumn(
                modifier = Modifier.padding(

                )
            ) {
                itemsIndexed(uiState.chains) { index, chain ->

                    Column() {
                        Row(
                            modifier = Modifier
                                .clickOnce(onClick = {
                                    onChainClick(chain)
                                })
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(chain.logo),
                                contentDescription = chain.name,
                                modifier = Modifier.size(
                                    22.dp
                                )
                            )

                            UiSpacer(
                                size = 24.dp
                            )

                            Text(
                                text = chain.ticker.uppercase(),
                                style = Theme.brockmann.body.s.medium,
                                color = Theme.v2.colors.text.primary
                            )

                            UiSpacer(
                                size = 24.dp
                            )

                            V2Container(
                                cornerType = CornerType.Circular,
                                borderType = ContainerBorderType.Bordered(),
                                type = ContainerType.SECONDARY,
                            ) {
                                Text(
                                    modifier = Modifier.padding(
                                        vertical = 4.dp,
                                        horizontal = 16.dp
                                    ),
                                    text = chain.name,
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.v2.colors.text.primary
                                )
                            }
                        }
                        if (index != uiState.chains.lastIndex) {
                            UiHorizontalDivider()
                        }
                    }
                }
            }
        }

    }
}

@Preview
@Composable
private fun ReceiveContentPreview() {
    ReceiveContent(
        uiState = ReceiveUiModel(
            listOf(
                ChainToReceiveUiModel(
                    name = "Ethereum",
                    logo = R.drawable.ethereum,
                    ticker = "ETH",
                    address = "0x1234",
                ),
                ChainToReceiveUiModel(
                    name = "Bitcoin",
                    logo = R.drawable.bitcoin,
                    ticker = "BTC",
                    address = "0x1234",
                )
            )
        ),
        searchFieldState = TextFieldState(),
        onChainClick = {}
    )
}