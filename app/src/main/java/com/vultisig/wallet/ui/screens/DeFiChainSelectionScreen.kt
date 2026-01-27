package com.vultisig.wallet.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel.SingleToken
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel.TokenUiSingle
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
                color = Theme.v2.colors.neutrals.n100,
            )
        },
        items = uiState.defiChains.map {
            SingleToken(
                data = it
            )
        },
        mapper = { it: SingleToken<SelectableDefiChainUiModel> ->
            TokenSelectionGridUiModel(
                tokenSelectionUiModel = TokenUiSingle(
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