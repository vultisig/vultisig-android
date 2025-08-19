package com.vultisig.wallet.ui.screens.referral

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel

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

}