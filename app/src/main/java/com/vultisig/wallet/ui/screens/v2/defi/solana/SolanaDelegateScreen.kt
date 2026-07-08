package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.solanastaking.SolanaDelegateViewModel
import com.vultisig.wallet.ui.models.solanastaking.SolanaValidatorOption
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun SolanaDelegateScreen(viewModel: SolanaDelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        VsTopAppBar(
            title = stringResource(R.string.solana_delegate_title),
            onBackClick = viewModel::back,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                VsTextInputField(
                    textFieldState = viewModel.amountFieldState,
                    hint = stringResource(R.string.solana_delegate_amount_hint),
                    keyboardType = KeyboardType.Decimal,
                )
                UiSpacer(size = 8.dp)
                Text(
                    text = stringResource(R.string.solana_delegate_select_validator),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            items(state.validators, key = { it.votePubkey }) { validator ->
                ValidatorRow(
                    validator = validator,
                    selected = validator.votePubkey == state.selectedVotePubkey,
                    onClick = { viewModel.onValidatorSelected(validator.votePubkey) },
                )
            }
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

        VsButton(
            label = stringResource(R.string.solana_delegate_cta),
            variant = VsButtonVariant.CTA,
            state =
                if (state.selectedVotePubkey != null && !state.isSubmitting) VsButtonState.Enabled
                else VsButtonState.Disabled,
            isLoading = state.isSubmitting,
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun ValidatorRow(validator: SolanaValidatorOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color =
                        if (selected) Theme.v2.colors.primary.accent4
                        else Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = validator.name,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 2.dp)
            Text(
                text =
                    stringResource(
                        R.string.solana_delegate_commission,
                        validator.commissionDisplay,
                    ),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
        }
        validator.apyDisplay?.let {
            Text(
                text = stringResource(R.string.solana_staking_apy_label, it),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.success,
            )
        }
    }
}
