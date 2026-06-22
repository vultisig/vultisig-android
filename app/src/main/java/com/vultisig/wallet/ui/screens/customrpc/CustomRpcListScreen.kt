package com.vultisig.wallet.ui.screens.customrpc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.models.customrpc.CustomRpcChainUiModel
import com.vultisig.wallet.ui.models.customrpc.CustomRpcListUiState
import com.vultisig.wallet.ui.models.customrpc.CustomRpcListViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CustomRpcListScreen() {
    val viewModel = hiltViewModel<CustomRpcListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CustomRpcListScreen(
        state = state,
        searchTextFieldState = viewModel.searchTextFieldState,
        onChainClick = viewModel::onChainClick,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun CustomRpcListScreen(
    state: CustomRpcListUiState,
    searchTextFieldState: TextFieldState,
    onChainClick: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(R.string.custom_rpc_title), onBackClick = onBackClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.custom_rpc_select_chain),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(size = 16.dp)

            SearchBar(state = searchTextFieldState, isInitiallyFocused = false)

            UiSpacer(size = 24.dp)

            if (state.chains.isEmpty()) {
                NoFoundContent(message = stringResource(R.string.custom_rpc_no_results))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.chains, key = { it.chainId }) { chain ->
                        CustomRpcChainTile(chain = chain, onClick = { onChainClick(chain.chainId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRpcChainTile(chain: CustomRpcChainUiModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Theme.v2.colors.backgrounds.surface1)
                    .then(
                        if (chain.isCustom)
                            Modifier.border(
                                width = 1.5.dp,
                                color = Theme.v2.colors.border.light,
                                shape = RoundedCornerShape(24.dp),
                            )
                        else Modifier
                    ),
        ) {
            Image(
                painter = painterResource(id = chain.logo),
                contentDescription = chain.chainName,
                modifier = Modifier.size(40.dp),
            )

            if (chain.isCustom) {
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(topStart = 16.dp, bottomEnd = 24.dp))
                            .background(Theme.v2.colors.border.light)
                            .padding(8.dp)
                ) {
                    UiIcon(
                        drawableResId = R.drawable.ic_edit_pencil,
                        size = 12.dp,
                        tint = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        UiSpacer(size = 11.dp)

        Text(
            text = chain.chainName,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.widthIn(max = 74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun CustomRpcListScreenPreview() {
    CustomRpcListScreen(
        state =
            CustomRpcListUiState(
                chains =
                    listOf(
                        CustomRpcChainUiModel(
                            chainId = "Ethereum",
                            chainName = "Ethereum",
                            logo = R.drawable.ethereum,
                            isCustom = true,
                        ),
                        CustomRpcChainUiModel(
                            chainId = "Arbitrum",
                            chainName = "Arbitrum",
                            logo = R.drawable.arbitrum,
                            isCustom = false,
                        ),
                        CustomRpcChainUiModel(
                            chainId = "Base",
                            chainName = "Base",
                            logo = R.drawable.base,
                            isCustom = false,
                        ),
                        CustomRpcChainUiModel(
                            chainId = "Akash",
                            chainName = "Akash",
                            logo = R.drawable.akash,
                            isCustom = false,
                        ),
                    )
            ),
        searchTextFieldState = TextFieldState(),
        onChainClick = {},
        onBackClick = {},
    )
}
