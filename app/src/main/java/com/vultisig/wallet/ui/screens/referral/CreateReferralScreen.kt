package com.vultisig.wallet.ui.screens.referral

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CreateReferralScreen(
    navController: NavController,
    model: CreateReferralViewModel = hiltViewModel(),
){
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_screen_title),
                onBackClick = {
                    navController.popBackStack()
                },
            )
        },
        content = { paddingValues ->

        }
    )
}