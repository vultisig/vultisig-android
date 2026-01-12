package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import app.rive.runtime.kotlin.RiveAnimationView
import com.vultisig.wallet.R
import com.vultisig.wallet.R.string
import com.vultisig.wallet.R.string.onboarding_summary_check
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.models.onboarding.VaultBackupSummaryUiModel
import com.vultisig.wallet.ui.models.onboarding.VaultBackupSummaryViewModel
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultBackupSummaryScreen(
    model: VaultBackupSummaryViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    BlockBackClick()

    VultBackupSummaryScreen(
        state = state,
        onNext = model::next,
        onConsentToggleCheck = model::toggleCheck,
        onChooseChains = model::chooseChains,
    )
}

@Composable
private fun VultBackupSummaryScreen(
    state: VaultBackupSummaryUiModel,
    onConsentToggleCheck: (Boolean) -> Unit,
    onNext: () -> Unit,
    onChooseChains: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .background(Theme.v2.colors.backgrounds.primary)
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            RiveAnimation(
                modifier = Modifier
                    .align(Alignment.Center),
                animation = when (state.vaultType) {
                    VaultType.Secure -> R.raw.riv_securevault_summary
                    VaultType.Fast -> R.raw.riv_fastvault_summary
                },
                onInit = { it: RiveAnimationView ->
                    if (state.vaultType == VaultType.Secure) {
                        it.setTextRunValue("numberOfVaults", state.vaultShares.toString())
                    }
                }
            )
        }

        UiSpacer(1f)

        VsCheckField(
            modifier = Modifier
                .padding(20.dp)
                .testTag("SummaryScreen.agree"),
            title = stringResource(id = onboarding_summary_check),
            isChecked = state.isConsentChecked,
            onCheckedChange = onConsentToggleCheck,
        )

        VsButton(
            onClick = onNext,
            label = stringResource(id = string.vault_backup_summary_start_using_vault),
            state = if (state.isConsentChecked)
                VsButtonState.Enabled else
                VsButtonState.Disabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("SummaryScreen.continue")
        )


        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                color = Theme.v2.colors.border.light,
            )

            Text(
                text = stringResource(R.string.referral_or),
                modifier = Modifier.padding(16.dp),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.supplementary.caption,
                textAlign = TextAlign.Center,
            )

            HorizontalDivider(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                color = Theme.v2.colors.border.light,
            )
        }


        VsButton(
            onClick = onChooseChains,
            label = stringResource(id = string.vault_backup_summary_choose_chains),
            variant = VsButtonVariant.Secondary,
            state = if (state.isConsentChecked)
                VsButtonState.Enabled else
                VsButtonState.Disabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        UiSpacer(32.dp)
    }
}

@Preview
@Composable
private fun VultBackupSummaryScreenPreview(){
    VultBackupSummaryScreen(
        state = VaultBackupSummaryUiModel(
            vaultType = VaultType.Secure,
            vaultShares = 3,
        ),
        onConsentToggleCheck = {},
        onChooseChains = {},
        onNext = {},
    )
}