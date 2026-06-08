package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.models.keygen.VaultConfirmationViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultConfirmationScreen(model: VaultConfirmationViewModel = hiltViewModel()) {
    BlockBackClick()

    // The ViewModel handles the timed navigation back Home; the screen only renders the
    // "Vault upgraded" confirmation shown on the Migrate (vault-upgrade) path.
    VaultConfirmationScreen()
}

@Composable
private fun VaultConfirmationScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary),
    ) {
        RiveAnimation(
            animation = R.raw.riv_upgrade_succes,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Text(
            text =
                buildAnnotatedString {
                    appendLine(stringResource(R.string.vault_confirmation_vault_upgraded))
                    withStyle(SpanStyle(brush = Theme.v2.colors.gradients.primary)) {
                        append(stringResource(R.string.vault_created_success_part_2))
                    }
                },
            style = Theme.brockmann.headings.largeTitle,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(all = 24.dp),
        )

        UiSpacer(36.dp)
    }
}

@Preview
@Composable
private fun VaultConfirmationScreenPreview() {
    VaultConfirmationScreen()
}
