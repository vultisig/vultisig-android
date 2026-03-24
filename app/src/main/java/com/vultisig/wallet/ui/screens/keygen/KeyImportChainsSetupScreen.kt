package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.core.Fit
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
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
internal fun KeyImportChainsSetupScreen(model: KeyImportChainsSetupViewModel = hiltViewModel()) {
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
            ChainsSetupState.Scanning -> ScanningContent(onSelectManually = onSelectManually)

            ChainsSetupState.ActiveChains ->
                ActiveChainsContent(
                    chains = state.activeChains,
                    onContinue = onContinue,
                    onCustomize = onCustomize,
                )

            ChainsSetupState.NoActiveChains ->
                NoActiveChainsContent(onSelectManually = onSelectManually)

            ChainsSetupState.CustomizeChains ->
                CustomizeChainsContent(
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
private fun ScanningContent(onSelectManually: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RiveAnimation(
                animation = R.raw.riv_connecting_with_server,
                modifier = Modifier.size(24.dp),
                fit = Fit.CONTAIN,
            )

            UiSpacer(24.dp)

            Text(
                text = stringResource(R.string.key_import_chains_scanning),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(12.dp)

            val descText = buildAnnotatedString {
                append(stringResource(R.string.key_import_chains_scanning_desc))
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) {
                    append(stringResource(R.string.key_import_chains_scanning_desc_highlight))
                }
            }

            Text(
                text = descText,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        VsButton(
            label = stringResource(R.string.key_import_chains_select_manually),
            variant = VsButtonVariant.Secondary,
            onClick = onSelectManually,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(16.dp)
    }
}

@Composable
private fun ActiveChainsContent(
    chains: List<ChainItemUiModel>,
    onContinue: () -> Unit,
    onCustomize: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiSpacer(16.dp)

            Image(
                painter = painterResource(R.drawable.ic_active_chain),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )

            UiSpacer(24.dp)

            Text(
                text =
                    pluralStringResource(
                        R.plurals.key_import_chains_found_title,
                        chains.size,
                        chains.size,
                    ),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            UiSpacer(8.dp)

            Text(
                text = stringResource(R.string.key_import_chains_found_desc),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        UiSpacer(32.dp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(chains, key = { it.chain.id }) { item -> ActiveChainItem(chain = item.chain) }
        }

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.key_import_chains_continue),
            variant = VsButtonVariant.CTA,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(20.dp)

        Row(
            modifier =
                Modifier.fillMaxWidth().clickable(onClick = onCustomize).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_pencil),
                contentDescription = null,
                tint = Theme.v2.colors.text.primary,
                modifier = Modifier.size(16.dp),
            )
            UiSpacer(8.dp)
            Text(
                text = stringResource(R.string.key_import_chains_customize),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary,
            )
        }

        UiSpacer(16.dp)
    }
}

@Composable
private fun NoActiveChainsContent(onSelectManually: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(state = searchTextFieldState, isInitiallyFocused = false)

        UiSpacer(14.dp)

        if (chains.isEmpty()) {
            NoFoundContent(message = stringResource(R.string.key_import_chains_no_results))
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 74.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(items = chains, key = { it.chain.id }, contentType = { "chain_grid_item" }) {
                    item ->
                    GridItem(
                        uiModel =
                            TokenSelectionGridUiModel(
                                tokenSelectionUiModel =
                                    TokenSelectionUiModel.TokenUiSingle(
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
private fun ActiveChainItem(chain: Chain) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Theme.v2.colors.variables.backgroundsSurface1)
                .border(1.dp, Theme.v2.colors.variables.bordersLight, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(chain.logo),
            contentDescription = chain.raw,
            modifier = Modifier.size(36.dp),
        )
        UiSpacer(4.dp)
        Text(
            text = chain.raw,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}
