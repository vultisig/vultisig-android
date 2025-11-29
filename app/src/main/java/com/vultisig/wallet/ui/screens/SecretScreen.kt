package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.SelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.SecretUiModel
import com.vultisig.wallet.ui.models.SecretViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SecretScreen(
    navController: NavController,
    model: SecretViewModel = hiltViewModel()
) {
    val state by model.state.collectAsState()

    SecretScreen(
        navController = navController,
        state = state,
        onToggleDkls = model::toggleDkls,
    )
}

@Composable
private fun SecretScreen(
    navController: NavController,
    state: SecretUiModel,
    onToggleDkls: (Boolean) -> Unit
) {

    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            TopBar(
                navController = navController,
                startIcon = R.drawable.ic_caret_left,
                centerText = stringResource(R.string.vault_settings_title)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionItem(
                title = stringResource(R.string.secret_enable_dkls),
                isChecked = state.isDklsEnabled,
                onCheckedChange = onToggleDkls
            )
        }
    }
}

@Preview
@Composable
private fun SecretScreenPreview() {
    SecretScreen(
        navController = rememberNavController(),
        state = SecretUiModel(),
        onToggleDkls = {}
    )
}