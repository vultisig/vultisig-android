package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.SelectionItem
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.SecretUiModel
import com.vultisig.wallet.ui.models.SecretViewModel

@Composable
internal fun SecretScreen(
    model: SecretViewModel = hiltViewModel()
) {
    val state by model.state.collectAsState()

    SecretScreen(
        state = state,
        onBackClick = model::back,
        onToggleDkls = model::toggleDkls,
    )
}

@Composable
private fun SecretScreen(
    state: SecretUiModel,
    onBackClick: ()-> Unit,
    onToggleDkls: (Boolean) -> Unit
) {

    VsScaffold(
        title = stringResource(R.string.vault_settings_title),
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
        state = SecretUiModel(),
        onBackClick = {},
        onToggleDkls = {}
    )
}