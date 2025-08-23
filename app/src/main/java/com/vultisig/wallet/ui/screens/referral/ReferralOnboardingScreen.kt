package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.referral.VsPromoBox
import com.vultisig.wallet.ui.components.referral.VsPromoTag
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.OnBoardingReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(
    navController: NavController,
    model: OnBoardingReferralViewModel = hiltViewModel(),
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_onboarding_title),
                onBackClick = {
                    navController.popBackStack()
                },
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                ReferralTag()

                HowItWorksTitle()

                TimeLineList()
            }
        },
        bottomBar = {
            FooterButton(model)
        }
    )
}

@Composable
private fun FooterButton(model: OnBoardingReferralViewModel) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
    ) {
        VsButton(
            onClick = {
                model.onClickGetStarted()
            },
            label = stringResource(R.string.referral_onboarding_get_started),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun TimeLineList() {
    TimelineItem(
        hasLineBelow = true,
        spacerHeight = 16.dp
    ) {
        VsPromoBox(
            title = stringResource(R.string.referral_create_code_title),
            description = stringResource(R.string.referral_create_code_description),
            icon = R.drawable.ic_referral,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    TimelineItem(
        hasLineBelow = true,
        spacerHeight = 16.dp
    ) {
        VsPromoBox(
            title = stringResource(R.string.referral_share_title),
            description = stringResource(R.string.referral_share_description),
            icon = R.drawable.ic_share_referral,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    TimelineItem(
        hasLineBelow = true,
        spacerHeight = 16.dp
    ) {
        VsPromoBox(
            title = stringResource(R.string.referral_earn_title),
            description = stringResource(R.string.referral_earn_description),
            icon = R.drawable.ic_cup,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    TimelineItem(
        hasLineBelow = false,
        spacerHeight = 0.dp
    ) {
        VsPromoBox(
            title = stringResource(R.string.referral_use_code_title),
            description = stringResource(R.string.referral_use_code_description),
            icon = R.drawable.ic_user,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HowItWorksTitle() {
    Box {
        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .width(1.dp)
                .height(110.dp)
                .background(Theme.colors.borders.light)
        )

        Text(
            text = stringResource(R.string.referral_how_it_works),
            style = Theme.brockmann.headings.largeTitle,
            color = Theme.colors.text.primary,
            modifier = Modifier.padding(top = 32.dp, bottom = 32.dp, start = 48.dp)
        )
    }
}

@Composable
private fun ReferralTag() {
    Box {
        Box(
            modifier = Modifier
                .padding(start = 24.dp, top = 20.dp)
                .width(1.dp)
                .wrapContentHeight()
                .background(Theme.colors.borders.light)
        )
        VsPromoTag(
            icon = R.drawable.ic_trumpet,
            text = stringResource(R.string.referral_program_tag)
        )
    }
}

@Composable
private fun TimelineItem(
    hasLineBelow: Boolean = true,
    spacerHeight: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box {
        // Vertical line that goes through item
        Column {
            val verticalHeight = if (hasLineBelow) {
                72.dp
            } else {
                24.dp
            }
            Box(
                modifier = Modifier
                    .padding(start = 24.dp)
                    .width(1.dp)
                    .height(verticalHeight)
                    .background(Theme.colors.borders.light)
            )

            // If last one, only draw half
            if (hasLineBelow && spacerHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .width(1.dp)
                        .height(spacerHeight)
                        .background(Theme.colors.borders.light)
                )
            }
        }

        // Horizontal line + actual item
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 25.dp)
                        .width(23.dp)
                        .height(1.dp)
                        .background(Theme.colors.borders.light)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                content()
            }
        }
    }
}