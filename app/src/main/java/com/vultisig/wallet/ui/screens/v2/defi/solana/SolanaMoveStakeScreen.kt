package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.solanastaking.SolanaMoveStakeStep
import com.vultisig.wallet.ui.models.solanastaking.SolanaMoveStakeViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun SolanaMoveStakeScreen(viewModel: SolanaMoveStakeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        VsTopAppBar(
            title = stringResource(R.string.solana_move_title),
            onBackClick = viewModel::back,
        )

        Column(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.solana_move_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )

            StepRow(
                index = 1,
                label = stringResource(R.string.solana_move_step_deactivate),
                active = state.step == SolanaMoveStakeStep.Deactivate,
                done = state.step.ordinal > SolanaMoveStakeStep.Deactivate.ordinal,
            )
            StepRow(
                index = 2,
                label =
                    state.unlocksAtEpoch?.let {
                        stringResource(R.string.solana_move_step_waiting_at, it.toString())
                    } ?: stringResource(R.string.solana_move_step_waiting),
                active = state.step == SolanaMoveStakeStep.Waiting,
                done = state.step.ordinal > SolanaMoveStakeStep.Waiting.ordinal,
            )
            StepRow(
                index = 3,
                label = stringResource(R.string.solana_move_step_withdraw),
                active = state.step == SolanaMoveStakeStep.Withdraw,
                done = state.step == SolanaMoveStakeStep.Done,
            )
        }

        state.error?.let {
            Text(
                text = it.asString(),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            UiSpacer(size = 8.dp)
        }

        MoveCta(
            step = state.step,
            isSubmitting = state.isSubmitting,
            onDeactivate = viewModel::onDeactivate,
            onWithdraw = viewModel::onWithdraw,
            onStakeToNew = viewModel::onStakeToNewValidator,
        )
    }
}

@Composable
private fun MoveCta(
    step: SolanaMoveStakeStep,
    isSubmitting: Boolean,
    onDeactivate: () -> Unit,
    onWithdraw: () -> Unit,
    onStakeToNew: () -> Unit,
) {
    when (step) {
        SolanaMoveStakeStep.Deactivate ->
            VsButton(
                label = stringResource(R.string.solana_staking_unstake_cta),
                variant = VsButtonVariant.CTA,
                state = if (isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
                isLoading = isSubmitting,
                onClick = onDeactivate,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        SolanaMoveStakeStep.Waiting ->
            VsButton(
                label = stringResource(R.string.solana_move_waiting_cta),
                variant = VsButtonVariant.CTA,
                state = VsButtonState.Disabled,
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        SolanaMoveStakeStep.Withdraw ->
            VsButton(
                label = stringResource(R.string.solana_staking_withdraw_cta),
                variant = VsButtonVariant.CTA,
                state = if (isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
                isLoading = isSubmitting,
                onClick = onWithdraw,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        SolanaMoveStakeStep.Done ->
            VsButton(
                label = stringResource(R.string.solana_move_stake_to_new_cta),
                variant = VsButtonVariant.CTA,
                onClick = onStakeToNew,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
    }
}

@Composable
private fun StepRow(index: Int, label: String, active: Boolean, done: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color =
                        if (active) Theme.v2.colors.primary.accent4
                        else Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (done) "✓" else index.toString(),
            style = Theme.brockmann.body.m.medium,
            color = if (done) Theme.v2.colors.alerts.success else Theme.v2.colors.text.primary,
        )
        UiSpacer(size = 12.dp)
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = if (active) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary,
        )
    }
}
