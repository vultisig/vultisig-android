package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.R.string
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v3.V3Icon
import com.vultisig.wallet.ui.models.onboarding.VaultBackupSummaryUiModel
import com.vultisig.wallet.ui.models.onboarding.VaultBackupSummaryViewModel
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import com.vultisig.wallet.ui.theme.Theme

@ExperimentalMaterial3Api
@Composable
internal fun VaultBackupSummaryScreen(model: VaultBackupSummaryViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()

    BlockBackClick()

    V2BottomSheet(
        onDismissRequest = {
            // no-op
        }
    ) {
        VultBackupSummaryScreen(
            state = state,
            onNext = model::next,
            onChooseChains = model::chooseChains,
        )
    }
}

@Composable
private fun VultBackupSummaryScreen(
    state: VaultBackupSummaryUiModel,
    onNext: () -> Unit,
    onChooseChains: () -> Unit,
) {
    Column(
        modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RiveAnimation(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            animation = R.raw.riv_onboarding_success,
        )

        UiSpacer(size = 24.dp)

        V3Icon(
            logo = R.drawable.tick_shield,
            shinedBottom = Theme.v2.colors.alerts.success,
            tintColor = Theme.v2.colors.alerts.success,
            borderWidth = 2.dp,
        )

        UiSpacer(size = 24.dp)

        Text(
            text = stringResource(R.string.backup_congrats),
            style = Theme.brockmann.headings.title2.copy(brush = Theme.v2.colors.gradients.primary),
        )

        UiSpacer(size = 8.dp)

        Text(
            text = stringResource(R.string.backup_your_vault_is_ready_to_use),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 12.dp)

        Text(
            text = stringResource(R.string.backup_you_re_all_set),
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.body.s.medium,
            modifier = Modifier.padding(horizontal = 48.dp),
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 48.dp)

        VsButton(
            onClick = onNext,
            label = stringResource(id = string.backup_go_to_wallet),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("SummaryScreen.continue"),
        )

        UiSpacer(size = 16.dp)

        VsButton(
            onClick = onChooseChains,
            label = stringResource(id = string.vault_backup_summary_choose_chains),
            variant = VsButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        UiSpacer(32.dp)
    }
}

@Preview
@Composable
private fun VultBackupSummaryScreenPreview() {
    VultBackupSummaryScreen(
        state = VaultBackupSummaryUiModel(vaultType = VaultType.Secure, vaultShares = 3),
        onChooseChains = {},
        onNext = {},
    )
}
