package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.securityscanner.SettingsSecurityScannerBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.settings.SettingsLargeSwitch
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
        title = stringResource(id = R.string.vault_settings_onchain_security_title),
        onBackClick = onNavigateBack,
    ) {
        Column(
            modifier = Modifier.padding(top = CONTENT_TOP_OFFSET),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

            SettingsLargeSwitch(
                checked = uiModel.isSwitchEnabled,
                label = stringResource(id = R.string.vault_settings_security_screen_title_switch),
                onCheckedChange = onCheckChange,
            )

            if (uiModel.showWarningDialog) {
                SettingsSecurityScannerBottomSheet(
                    onContinueAnyway = onContinueAnyway,
                    onDismissRequest = onGoBack,
                )
            }
        }
    }
}

private val CONTENT_TOP_OFFSET = 9.dp
