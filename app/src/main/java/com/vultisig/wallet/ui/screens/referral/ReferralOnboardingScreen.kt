package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.referral.VsPromoBox
import com.vultisig.wallet.ui.components.referral.VsPromoTag
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.referral.OnBoardingReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(
    model: OnBoardingReferralViewModel = hiltViewModel(),
) {
    ReferralOnboardingScreen(
        onGetStarted = model::onClickGetStarted,
        onBackClick = model::back
    )
}


@Composable
private fun ReferralOnboardingScreen(
    onGetStarted: () -> Unit,
    onBackClick: () -> Unit,
){
    VsScaffold(
        title = stringResource(R.string.referral_onboarding_title),
        onBackClick = onBackClick,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ReferralTag()
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .width(1.dp)
                            .fillMaxHeight(0.92f)
                            .background(Theme.colors.border.light)
                    )

                    Column {
                        HowItWorksTitle()

                        TimeLineList()
                    }
                }
            }
        },
        bottomBar = {
            FooterButton(onGetStarted = onGetStarted)
        }
    )
}

@Composable
private fun FooterButton(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        VsButton(
            onClick = onGetStarted,
            label = stringResource(R.string.referral_onboarding_get_started),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun TimeLineList() {
    Column(modifier = Modifier.padding(end = 16.dp)) {
        TimeLineItem {
            VsPromoBox(
                title = stringResource(R.string.referral_create_code_title),
                description = stringResource(R.string.referral_create_code_description),
                icon = R.drawable.ic_referral,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        UiSpacer(16.dp)

        TimeLineItem {
            VsPromoBox(
                title = stringResource(R.string.referral_share_title),
                description = stringResource(R.string.referral_share_description),
                icon = R.drawable.ic_share_referral,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        UiSpacer(16.dp)

        TimeLineItem {
            VsPromoBox(
                title = stringResource(R.string.referral_earn_title),
                description = stringResource(R.string.referral_earn_description),
                icon = R.drawable.ic_cup,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        UiSpacer(16.dp)

        TimeLineItem {
            VsPromoBox(
                title = stringResource(R.string.referral_use_code_title),
                description = stringResource(R.string.referral_use_code_description),
                icon = R.drawable.ic_user,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimeLineItem(
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(1.dp)
                .background(Theme.colors.border.light)
        )

        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            content()
        }
    }
}

@Composable
private fun ReferralTag() {
    Box {
        VsPromoTag(
            icon = R.drawable.ic_trumpet,
            text = stringResource(R.string.referral_program_tag)
        )
    }
}

@Composable
private fun HowItWorksTitle() {
    Box {
        Text(
            text = stringResource(R.string.referral_how_it_works),
            style = Theme.brockmann.headings.largeTitle,
            color = Theme.colors.text.primary,
            modifier = Modifier.padding(top = 32.dp, bottom = 32.dp, start = 48.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReferralOnboardingScreenPreview() {
    ReferralOnboardingScreen(
        onGetStarted = {},
        onBackClick = {}
    )
}