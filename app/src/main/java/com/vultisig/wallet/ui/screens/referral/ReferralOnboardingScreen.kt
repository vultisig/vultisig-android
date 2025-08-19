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
                icon = R.drawable.ic_trumpet,
                text = stringResource(R.string.referral_program_tag)
            )

            Text(
                text = stringResource(R.string.referral_how_it_works),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.colors.text.primary,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp, start = 48.dp)
            )

            VsPromoBox(
                title = stringResource(R.string.referral_create_code_title),
                description = stringResource(R.string.referral_create_code_description),
                icon = R.drawable.ic_referral,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = stringResource(R.string.referral_share_title),
                description = stringResource(R.string.referral_share_description),
                icon = R.drawable.ic_share_referral,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = stringResource(R.string.referral_earn_title),
                description = stringResource(R.string.referral_earn_description),
                icon = R.drawable.ic_cup,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp),
            )

            UiSpacer(16.dp)

            VsPromoBox(
                title = stringResource(R.string.referral_use_code_title),
                description = stringResource(R.string.referral_use_code_description),
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