package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.models.keygen.VaultConfirmationViewModel
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultConfirmationScreen(
    model: VaultConfirmationViewModel = hiltViewModel(),
) {
    BlockBackClick()

    val state by model.state.collectAsState()

    VaultConfirmationScreen(
        vaultInfo = state.vaultInfo,
        action = state.action,
    )
}

@Composable
private fun VaultConfirmationScreen(
    vaultInfo: VaultType,
    action: TssAction?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.primary),
    ) {
        RiveAnimation(
            animation = when (action) {
                TssAction.Migrate -> R.raw.riv_upgrade_succes
                else -> when (vaultInfo) {
                    VaultType.Secure -> R.raw.riv_fastvault_backup_succes
                    VaultType.Fast -> R.raw.riv_fastvault_backup_succes
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        val wellDoneText = when (action) {
            TssAction.Migrate -> buildAnnotatedString {
                appendLine(stringResource(R.string.vault_confirmation_vault_upgraded))
                withStyle(
                    SpanStyle(
                        brush = Theme.colors.gradients.primary,
                    )
                ) {
                    append(stringResource(R.string.vault_created_success_part_2))
                }
            }

            else -> buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        brush = Theme.colors.gradients.primary,
                    )
                ) {
                    appendLine(stringResource(R.string.vault_confirmation_well_done))
                }
                append(stringResource(R.string.vault_confirmation_you_re_ready_to_use_a_new_wallet_standard))
            }
        }

        Text(
            text = wellDoneText,
            style = Theme.brockmann.headings.largeTitle,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(all = 24.dp),
        )

        UiSpacer(36.dp)

        RiveAnimation(
            animation = R.raw.riv_connecting_with_server,
            modifier = Modifier
                .size(36.dp)
        )

        UiSpacer(70.dp)
    }
}

@Preview
@Composable
private fun VaultConfirmationScreenPreview() {
    VaultConfirmationScreen(
        vaultInfo = VaultType.Secure,
        action = TssAction.Migrate,
    )
}

