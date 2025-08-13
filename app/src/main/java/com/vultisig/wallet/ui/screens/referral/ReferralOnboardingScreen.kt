package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(
    navController: NavController,
) {
    BlockBackClick()

    Column(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .background(Theme.colors.backgrounds.primary)
                .weight(1f)
                .fillMaxSize(),
        ) {
            RiveAnimation(
                modifier = Modifier
                    .align(Alignment.Center),
                animation = R.raw.riv_securevault_summary, // Waiting for designer to provider riv
            )
        }

        VsButton(
            onClick = { },
            label = "Get Started",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        UiSpacer(32.dp)
    }
}