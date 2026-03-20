package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.components.securityscanner.SettingsSecurityScannerBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SecurityScannerEnableScreen(
    navController: NavController,
    viewModel: SecurityScannerEnableViewModel = hiltViewModel(),
) {
    val model by viewModel.uiModel.collectAsState()

    SecurityScannerEnableScreen(
        uiModel = model,
        onNavigateBack = navController::navigateUp,
        onCheckChange = { viewModel.onCheckedChange(it) },
        onContinueAnyway = viewModel::onContinueSecurity,
        onGoBack = viewModel::onDismiss,
    )
}

@Composable
private fun SecurityScannerEnableScreen(
    uiModel: SecurityScannerEnableUiModel,
    onNavigateBack: () -> Unit,
    onCheckChange: (Boolean) -> Unit = {},
    onGoBack: () -> Unit = {},
    onContinueAnyway: () -> Unit = {},
) {
    V2Scaffold(
        title = stringResource(id = R.string.vault_settings_security_screen_title),
        onBackClick = onNavigateBack,
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.vault_settings_security_screen_title_switch),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = stringResource(id = R.string.vault_settings_security_screen_title_content),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VsSwitch(checked = uiModel.isSwitchEnabled, onCheckedChange = onCheckChange)

                UiSpacer(12.dp)

                Text(
                    text = if (uiModel.isSwitchEnabled) "ON" else "OFF",
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }

            if (uiModel.showWarningDialog) {
                SettingsSecurityScannerBottomSheet(
                    onContinueAnyway = onContinueAnyway,
                    onDismissRequest = onGoBack,
                )
            }
        }
    }
}
