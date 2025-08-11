package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ReferralScreen(
    navController: NavController,
) {

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.migration_onboarding_upgrade_your_vault),
                onBackClick = {

                },
            )
        },
        content = { contentPadding ->
            ReferralContent(contentPadding)
        },
    )
}

@Composable
internal fun ReferralContent(paddingValues: PaddingValues) {

}