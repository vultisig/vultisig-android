package com.vultisig.wallet.ui.screens.referral

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.EditVaultReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralEditVaultScreen(
    navController: NavController,
    model: EditVaultReferralViewModel = hiltViewModel(),
) {

    ReferralEditVaultScreen(
        onBackPressed = navController::popBackStack,
    )
}

@Composable
private fun ReferralEditVaultScreen(
    onBackPressed: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_edit_referral),
                onBackClick = onBackPressed,
                iconRight = R.drawable.ic_info,
            )
        },
        content = {
            paddingValues ->
        },
        bottomBar = { }
    )
}