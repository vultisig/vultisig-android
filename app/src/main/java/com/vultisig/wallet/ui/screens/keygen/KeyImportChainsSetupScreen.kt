package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.components.v2.tokenitem.GridItem
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.keygen.ChainItemUiModel
import com.vultisig.wallet.ui.models.keygen.ChainsSetupState
import com.vultisig.wallet.ui.models.keygen.KeyImportChainsSetupUiModel
import com.vultisig.wallet.ui.models.keygen.KeyImportChainsSetupViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeyImportChainsSetupScreen(
    model: KeyImportChainsSetupViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    KeyImportChainsSetupContent(
        state = state,
        searchTextFieldState = model.searchTextFieldState,
        onBackClick = model::back,
        onSelectManually = model::selectManually,
        onCustomize = model::customize,
        onToggleChain = model::toggleChain,
        onContinue = model::continueWithSelection,
    )
}

@Composable
internal fun KeyImportChainsSetupContent(
    state: KeyImportChainsSetupUiModel,
    searchTextFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onSelectManually: () -> Unit,
    onCustomize: () -> Unit,
    onToggleChain: (Chain) -> Unit,
    onContinue: () -> Unit,
) {
    V2Scaffold(
        onBackClick = onBackClick,
        title = stringResource(R.string.key_import_chains_title),
    ) {
        when (state.screenState) {
            ChainsSetupState.Scanning -> ScanningContent(
                onSelectManually = onSelectManually,
            )

            ChainsSetupState.ActiveChains -> ActiveChainsContent(
                chains = state.activeChains,
                onContinue = onContinue,
                onCustomize = onCustomize,
            )

            ChainsSetupState.NoActiveChains -> NoActiveChainsContent(
                onSelectManually = onSelectManually,
            )

            ChainsSetupState.CustomizeChains -> CustomizeChainsContent(
                chains = state.filteredChains,
                selectedCount = state.selectedCount,
                searchTextFieldState = searchTextFieldState,
                onToggleChain = onToggleChain,
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun ScanningContent(
    onSelectManually: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = Theme.v2.colors.buttons.primary,
            modifier = Modifier.size(48.dp),
        )
        UiSpacer(24.dp)
        Text(
            text = stringResource(R.string.key_import_chains_scanning),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.v2.colors.text.primary,
        )
        UiSpacer(8.dp)
        Text(
            text = stringResource(R.string.key_import_chains_scanning_desc),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(32.dp)
        VsButton(
            label = stringResource(R.string.key_import_chains_select_manually),
            variant = VsButtonVariant.Secondary,
            onClick = onSelectManually,
        )
    }
}

@Composable
private fun ActiveChainsContent(
    chains: List<ChainItemUiModel>,
    onContinue: () -> Unit,
    onCustomize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.key_import_chains_found_title),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.v2.colors.text.primary,
        )
        UiSpacer(8.dp)
        Text(
            text = stringResource(R.string.key_import_chains_found_desc),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        UiSpacer(16.dp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chains) { item ->
                ChainListItem(chain = item.chain, isSelected = true)
            }
        }

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.key_import_chains_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        UiSpacer(8.dp)
        VsButton(
            label = stringResource(R.string.key_import_chains_customize),
            variant = VsButtonVariant.Secondary,
            onClick = onCustomize,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoActiveChainsContent(
    onSelectManually: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.key_import_chains_no_active_title),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(8.dp)
        Text(
            text = stringResource(R.string.key_import_chains_no_active_desc),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(32.dp)
        VsButton(
            label = stringResource(R.string.key_import_chains_select_manually),
            onClick = onSelectManually,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomizeChainsContent(
    chains: List<ChainItemUiModel>,
    selectedCount: Int,
    searchTextFieldState: TextFieldState,
    onToggleChain: (Chain) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        SearchBar(
            state = searchTextFieldState,
            onCancelClick = {},
            isInitiallyFocused = false,
        )

        UiSpacer(14.dp)

        if (chains.isEmpty()) {
            NoFoundContent(
                message = stringResource(R.string.key_import_chains_no_results),
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 74.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(
                    items = chains,
                    key = { it.chain.id },
                    contentType = { "chain_grid_item" },
                ) { item ->
                    GridItem(
                        uiModel = TokenSelectionGridUiModel(
                            tokenSelectionUiModel = TokenSelectionUiModel.TokenUiSingle(
                                name = item.chain.raw,
                                logo = item.chain.logo,
                            ),
                            isChecked = item.isSelected,
                        ),
                        onCheckedChange = { onToggleChain(item.chain) },
                    )
                }
            }
        }

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.key_import_chains_get_started),
            onClick = onContinue,
            variant = VsButtonVariant.CTA,
            state = if (selectedCount > 0) VsButtonState.Enabled else VsButtonState.Disabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChainListItem(
    chain: Chain,
    isSelected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.v2.colors.backgrounds.tertiary_2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(chain.logo),
            contentDescription = chain.raw,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = chain.raw,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.success,
            )
        }
    }
}
