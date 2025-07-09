package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar

@Composable
internal fun SecurityScannerEnableScreen(
    uiModel: SecurityScannerEnableUiModel,
    navController: NavController,
    viewModel: SecurityScannerEnableViewModel,
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
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.vault_settings_biometrics_screen_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            /*SelectionItem(
                title = stringResource(id = R.string.vault_settings_biometrics_screen_title),
                isChecked = uiModel.isSwitchEnabled,
                onCheckedChange = onCheckChange,
            ) */
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecurityScannerEnableScreen() {
    SecurityScannerEnableScreen(
        uiModel = SecurityScannerEnableUiModel(),
        navController = rememberNavController(),
        onCheckChange = {},
    )
}