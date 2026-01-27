package com.vultisig.wallet.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.DeFiChainSelectionViewModel
import com.vultisig.wallet.ui.models.SelectableDefiChainUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun DeFiChainSelectionScreen(
    viewModel: DeFiChainSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TokenSelectionList(
        titleContent = {
            Text(
                text = stringResource(R.string.chain_selection_select_defi_chains),
                style = Theme.brockmann.headings.title2,
                color = Theme.colors.neutrals.n100,
            )
        },
        items = uiState.defiChains.map {
            GridTokenUiModel.SingleToken(
                data = it
            )
        },
        mapper = { it: GridTokenUiModel.SingleToken<SelectableDefiChainUiModel> ->
            TokenSelectionGridUiModel(
                tokenSelectionUiModel = TokenSelectionUiModel.TokenUiSingle(
                    name = it.data.defiChain.raw,
                    logo = it.data.defiChain.logo,
                ),
                isChecked = it.data.isSelected
            )
        },
        searchTextFieldState = viewModel.searchTextFieldState,
        onDoneClick = viewModel::saveSelection,
        onCancelClick = viewModel::navigateBack,
        onCheckChange = viewModel::toggleChain,
        onSetSearchText = viewModel::onSearch,
        notFoundContent = {
            NoFoundContent(
                message = stringResource(R.string.chain_selection_no_chains_found)
            )
        },
    )
}