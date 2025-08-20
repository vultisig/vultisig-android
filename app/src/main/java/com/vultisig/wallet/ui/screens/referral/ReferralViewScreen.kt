package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralViewScreen(
    navController: NavController,
    model: ViewReferralViewModel = hiltViewModel(),
) {
    ReferralViewScreen(
        onBackPressed = navController::popBackStack,
    )
}

@Composable
internal fun ReferralViewScreen(
    onBackPressed: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Referral",
                onBackClick = {
                    onBackPressed()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

            }
        },
        bottomBar = {
            UiSpacer(32.dp)
        },
    )
}