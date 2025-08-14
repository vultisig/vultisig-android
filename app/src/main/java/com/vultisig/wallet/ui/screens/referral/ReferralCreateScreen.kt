package com.vultisig.wallet.ui.screens.referral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MoreInfoBox
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
    var hintBoxOffset by remember { mutableIntStateOf(0) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)

    // Get the status bar height in pixels (Int)
    val statusBarHeightPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    // Convert the pixel value to a Dp value
    val statusBarHeightDp = with(LocalDensity.current) { statusBarHeightPx.toDp() }


    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Create Referral",
                onBackClick = onBackPressed,
                iconRight = R.drawable.ic_question_mark,
            )
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                 MoreInfoBox(
                    text = stringResource(R.string.fast_vault_password_screen_hint),
                    title = stringResource(R.string.fast_vault_password_screen_hint_title),
                     modifier = Modifier
                         .padding(start = 16.dp, end = 16.dp)
                         .offset(y = statusBarHeightDp)
                         .clickable(onClick = {})
                )
            }
        },
        content = { paddingValues ->
            Column {

            }
        }
    )
}