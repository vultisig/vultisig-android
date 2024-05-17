package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.vultisig.wallet.R

@Composable
internal fun KeysignSessionIdDiscoveryScreen(
    navController: NavHostController,
) {
    KeysignLoadingScreen(
        navController = navController,
        text = stringResource(R.string.join_keysign_discovering_session_id),
    )
}