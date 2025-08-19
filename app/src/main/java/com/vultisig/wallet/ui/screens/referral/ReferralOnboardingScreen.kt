package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.referral.VsPromoBox
import com.vultisig.wallet.ui.components.referral.VsPromoTag
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.OnBoardingReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(
    navController: NavController,
    model: OnBoardingReferralViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .fillMaxSize(),
    ) {
        VsTopAppBar(
            title = stringResource(R.string.referral_onboarding_title),
            onBackClick = {
                navController.popBackStack()
            },
        )

        Column(
            modifier = Modifier
                .background(Theme.colors.backgrounds.primary)
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            VsPromoTag(
                icon = R.drawable.ic_cup,
                text = "Referral Program"
            )

            Text(
                text = "How it works",
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.colors.text.primary,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp, start = 48.dp)
            )

            VsPromoBox(
                title = "Create your referral code",
                description = "Pick a short code and set your reward payout.",
                icon = R.drawable.ic_referral,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = "Share with friends",
                description = "Invite friends to use your code while swapping.",
                icon = R.drawable.ic_share_referral,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = "Earn 10 bps of swaps automatically",
                description = "Get paid in your preferred asset every time they trade.",
                icon = R.drawable.ic_cup,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = "Use Referral code - Save 5bps",
                description = "Use a code from your friend and save on swap fees.",
                icon = R.drawable.ic_user,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(1f)

            VsButton(
                onClick = {
                    model.onClickGetStarted()
                },
                label = stringResource(R.string.referral_onboarding_get_started),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            )

            UiSpacer(32.dp)
        }
    }
}