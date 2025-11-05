package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.NoChainFound
import com.vultisig.wallet.ui.models.ChainSelectionUiModel
import com.vultisig.wallet.ui.models.ChainSelectionViewModel
import com.vultisig.wallet.ui.models.ChainUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainSelectionScreen(
    viewModel: ChainSelectionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    ChainSelectionScreen(
        state = state,
        searchTextFieldState = viewModel.searchTextFieldState,
        onEnableAccount = viewModel::enableAccountTemp,
        onDisableAccount = viewModel::disableAccountTemp,
        onDoneClick = viewModel::onCommitChanges,
        onCancelClick = viewModel::cancelChanges,
        onSetSearchText = viewModel::setSearchText,
    )
}

@Composable
private fun ChainSelectionScreen(
    state: ChainSelectionUiModel,
    searchTextFieldState: TextFieldState,
    onEnableAccount: (Coin) -> Unit,
    onDisableAccount: (Coin) -> Unit,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSetSearchText: (String) -> Unit = {},
) {

    TokenSelectionList(
        titleContent = {
            Text(
                text = stringResource(R.string.chain_selection_select_chains),
                style = Theme.brockmann.headings.title2,
                color = Theme.colors.neutrals.n100,
            )
        },
        items = state.chains.map {
            GridTokenUiModel.SingleToken(
                data = it
            )
        },
        mapper = {
            TokenSelectionGridUiModel(
                tokenSelectionUiModel = TokenSelectionUiModel.TokenUiSingle(
                    name = it.data.coin.chain.name,
                    logo = it.data.coin.chain.logo,
                ),
                isChecked = it.data.isEnabled
            )
        },
        searchTextFieldState = searchTextFieldState,
        onDoneClick = onDoneClick,
        onCancelClick = onCancelClick,
        notFoundContent = {
            NoChainFound()
        },
        onCheckChange = { checked, chain ->
            if (checked) {
                onEnableAccount(chain.coin)
            } else {
                onDisableAccount(chain.coin)
            }
        },
        onSetSearchText = onSetSearchText,
    )
}

@Preview
@Composable
fun ChainSelectionViewPreview() {
    ChainSelectionScreen(
        state = ChainSelectionUiModel(
            chains = listOf(
                ChainUiModel(
                    isEnabled = false,
                    coin = Coins.Ethereum.VULT
                ),
                ChainUiModel(
                    isEnabled = true,
                    coin = Coins.BscChain.ETH
                ),
                ChainUiModel(
                    isEnabled = false,
                    coin = Coins.CronosChain.CRO
                ),
                ChainUiModel(
                    isEnabled = true,
                    coin = Coins.Avalanche.BLS
                ),
            )
        ),
        searchTextFieldState = rememberTextFieldState(),
        onEnableAccount = {},
        onDisableAccount = {},
        onDoneClick = {},
        onCancelClick = {},
    )
}
