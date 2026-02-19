package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.vultisig.wallet.ui.models.keygen.ChainItemUiModel
import com.vultisig.wallet.ui.models.keygen.ChainsSetupState
import com.vultisig.wallet.ui.models.keygen.KeyImportChainsSetupViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeyImportChainsSetupScreen(
    model: KeyImportChainsSetupViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    V2Scaffold(
        onBackClick = model::back,
        title = stringResource(R.string.key_import_chains_title),
    ) {
        when (state.screenState) {
            ChainsSetupState.Scanning -> ScanningContent(
                onSelectManually = model::selectManually,
            )

            ChainsSetupState.ActiveChains -> ActiveChainsContent(
                chains = state.activeChains,
                onContinue = model::continueWithSelection,
                onCustomize = model::customize,
            )

            ChainsSetupState.NoActiveChains -> NoActiveChainsContent(
                onSelectManually = model::selectManually,
            )

            ChainsSetupState.CustomizeChains -> CustomizeChainsContent(
                chains = state.allChains,
                selectedCount = state.selectedCount,
                onToggleChain = model::toggleChain,
                onSelectAll = model::selectAll,
                onDeselectAll = model::deselectAll,
                onContinue = model::continueWithSelection,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizeChainsContent(
    chains: List<ChainItemUiModel>,
    selectedCount: Int,
    onToggleChain: (Chain) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.key_import_chains_select_title, selectedCount),
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.text.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VsButton(
                    label = stringResource(R.string.key_import_chains_all),
                    variant = VsButtonVariant.Secondary,
                    onClick = onSelectAll,
                )
                VsButton(
                    label = stringResource(R.string.key_import_chains_none),
                    variant = VsButtonVariant.Secondary,
                    onClick = onDeselectAll,
                )
            }
        }
        UiSpacer(16.dp)

        FlowRow(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chains.forEach { item ->
                ChainChip(
                    chain = item.chain,
                    isSelected = item.isSelected,
                    onClick = { onToggleChain(item.chain) },
                )
            }
        }

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.key_import_chains_continue),
            onClick = onContinue,
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

@Composable
private fun ChainChip(
    chain: Chain,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isSelected) Modifier.background(
                    Theme.v2.colors.buttons.primary.copy(alpha = 0.15f)
                )
                else Modifier
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Theme.v2.colors.buttons.primary
                else Theme.v2.colors.border.light,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(chain.logo),
            contentDescription = chain.raw,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
        )
        Text(
            text = chain.raw,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )
    }
}
