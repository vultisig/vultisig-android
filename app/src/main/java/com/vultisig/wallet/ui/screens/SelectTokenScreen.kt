package com.vultisig.wallet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.ui.models.SelectTokenViewModel


@Composable
internal fun SelectTokenScreen(
    navController: NavHostController,
    viewModel: SelectTokenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    TokenSelectionScreen(
        navController = navController,
        searchTextFieldState = viewModel.searchTextFieldState,
        state = state,
        hasTokenSwitch = false,
        hasCustomToken = false,
        onEnableToken = viewModel::enableToken,
        onDisableToken = {}
    )
}