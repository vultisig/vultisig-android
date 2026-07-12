package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.solanastaking.SolanaFinishMoveUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaFinishMoveViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/**
 * Solana move-stake step 2 ("Finish Move") screen. Shows the source stake account read-only and a
 * validator picker for the destination; Continue re-delegates the cooled-down account to the chosen
 * validator. Reuses the delegate screen's
 * [SolanaValidatorPickerField]/[SolanaValidatorPickerSheet]. Mirrors Windows
 * `SolanaFinishMoveSpecific`.
 */
@Composable
internal fun SolanaFinishMoveScreen(viewModel: SolanaFinishMoveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(
        title = stringResource(R.string.solana_finish_move_title),
        onBackClick = viewModel::back,
    ) {
        SolanaFinishMoveContent(
            state = state,
            onPickValidator = viewModel::openValidatorPicker,
            onSubmit = viewModel::submit,
        )

        if (state.isShowingPicker) {
            SolanaValidatorPickerSheet(
                isLoading = state.isLoading,
                searchQuery = state.validatorSearchQuery,
                selectedVotePubkey = state.selectedValidator?.votePubkey,
                validators = viewModel.visibleValidators(state),
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onValidatorSelected = viewModel::selectValidator,
                onDismiss = viewModel::closeValidatorPicker,
            )
        }
    }
}

@Composable
internal fun SolanaFinishMoveContent(
    state: SolanaFinishMoveUiState,
    onPickValidator: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val canContinue = state.selectedValidator != null && !state.isSubmitting

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.solana_staking_stake_account),
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
                UiSpacer(weight = 1f)
                Text(
                    text = state.stakePubkey,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            SolanaValidatorPickerField(
                selected = state.selectedValidator,
                onClick = onPickValidator,
            )

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.solana_finish_move_notice),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            state.error?.let {
                Text(
                    text = it.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }

        VsButton(
            label = stringResource(R.string.cosmos_staking_continue),
            variant = VsButtonVariant.CTA,
            state = if (canContinue) VsButtonState.Enabled else VsButtonState.Disabled,
            isLoading = state.isSubmitting,
            onClick = onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        )
    }
}
