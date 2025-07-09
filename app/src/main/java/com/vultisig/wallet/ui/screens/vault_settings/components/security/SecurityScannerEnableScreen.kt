package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
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
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.VaultSwitch
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SecurityScannerEnableScreen(
    navController: NavController,
    viewModel: SecurityScannerEnableViewModel = hiltViewModel(),
) {
    val model by viewModel.uiModel.collectAsState()

    SecurityScannerEnableScreen(
        uiModel = model,
        navController = navController,
        onCheckChange = { viewModel.onCheckedChange(it) }
    )
}

@Composable
private fun SecurityScannerEnableScreen(
    uiModel: SecurityScannerEnableUiModel,
    navController: NavController,
    onCheckChange: (Boolean) -> Unit = {},
    onGoBackSecurity: () -> Unit = {},
    onContinueSecurity: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.vault_settings_security_screen_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Manage your on-chain security",
                style = Theme.brockmann.body.m.medium,
                color = Theme.colors.text.primary
            )
            Text(
                text = "You can disable your realtime on-chain security",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.light
            )

            VaultSwitch(
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Theme.colors.neutral0,
                    checkedBorderColor = Theme.colors.turquoise800,
                    checkedTrackColor = Theme.colors.turquoise800,
                    uncheckedThumbColor = Theme.colors.neutral0,
                    uncheckedBorderColor = Theme.colors.oxfordBlue400,
                    uncheckedTrackColor = Theme.colors.oxfordBlue400
                ),
                checked = true,
                onCheckedChange = null,
            )

            if (uiModel.showWarningDialog) {
                // SHOW ALERT DIALOG
            }
        }
    }
}