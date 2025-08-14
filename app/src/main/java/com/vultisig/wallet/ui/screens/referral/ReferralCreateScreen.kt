package com.vultisig.wallet.ui.screens.referral

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralCreateScreen(
    navController: NavController,
    model: CreateReferralViewModel = hiltViewModel(),
){
    ReferralCreateScreen(
        onBackPressed = navController::popBackStack,
        onSearchClick = {},
        onAddClick = {},
        onSubtractClick = {}
    )
}

@Composable
private fun ReferralCreateScreen(
    onBackPressed: () -> Unit,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubtractClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Create Referral",
                onBackClick = onBackPressed,
                iconRight = R.drawable.ic_question_mark,
            )
        },
        content = { paddingValues ->

        }
    )
}