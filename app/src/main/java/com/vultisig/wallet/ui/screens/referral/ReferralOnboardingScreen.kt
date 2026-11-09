package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.referral.VsPromoBox
import com.vultisig.wallet.ui.components.referral.VsPromoTag
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.OnBoardingReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(model: OnBoardingReferralViewModel = hiltViewModel()) {
    ReferralOnboardingScreen(onGetStarted = model::onClickGetStarted, onBackClick = model::back)
}

@Composable
private fun ReferralOnboardingScreen(onGetStarted: () -> Unit, onBackClick: () -> Unit) {
    val timelineColor = Theme.v2.colors.backgrounds.surface1
    V2Scaffold(
        title = stringResource(R.string.referral_onboarding_title),
        onBackClick = onBackClick,
        applyDefaultPaddings = false,
        content = {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp)
            ) {
                VsPromoTag(
                    icon = R.drawable.megaphone,
                    text = stringResource(R.string.referral_program_tag),
                )

                Text(
                    text = stringResource(R.string.referral_how_it_works),
                    style = Theme.brockmann.headings.largeTitle,
                    color = Theme.v2.colors.text.primary,
                    modifier =
                        Modifier.fillMaxWidth()
                            .drawBehind {
                                drawRect(
                                    color = timelineColor,
                                    size = Size(TIMELINE_WIDTH.toPx(), size.height),
                                )
                            }
                            .padding(start = 20.dp, top = 32.dp, bottom = 48.dp),
                )

                TimeLineList(lineColor = timelineColor)
            }
        },
        bottomBar = { FooterButton(onGetStarted = onGetStarted) },
    )
}

@Composable
private fun FooterButton(onGetStarted: () -> Unit) {
    VsButton(
        onClick = onGetStarted,
        label = stringResource(R.string.referral_invite_next),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun TimeLineList(lineColor: Color) {
    val items =
        listOf(
            Triple(
                R.drawable.ic_referral_keyboard,
                R.string.referral_create_code_title,
                R.string.referral_create_code_description,
            ),
            Triple(
                R.drawable.share_up_right_2,
                R.string.referral_share_title,
                R.string.referral_share_description,
            ),
            Triple(
                R.drawable.ic_cup,
                R.string.referral_earn_title,
                R.string.referral_earn_description,
            ),
            Triple(
                R.drawable.ic_referral_users,
                R.string.referral_use_code_title,
                R.string.referral_use_code_description,
            ),
        )

    Column(verticalArrangement = Arrangement.spacedBy(TIMELINE_ROW_GAP)) {
        items.forEachIndexed { index, (icon, title, description) ->
            VsPromoBox(
                title = stringResource(title),
                description = stringResource(description),
                icon = icon,
                modifier =
                    Modifier.timeLineBranch(color = lineColor, isLast = index == items.lastIndex)
                        .padding(start = TIMELINE_BRANCH_LENGTH),
            )
        }
    }
}

/** Draws the trunk through this row and the branch from it to the row's vertical center. */
private fun Modifier.timeLineBranch(color: Color, isLast: Boolean) = drawBehind {
    val lineWidth = TIMELINE_WIDTH.toPx()
    val center = size.height / 2
    val trunkEnd = if (isLast) center + lineWidth / 2 else size.height + TIMELINE_ROW_GAP.toPx()
    drawRect(color = color, size = Size(lineWidth, trunkEnd))
    drawRect(
        color = color,
        topLeft = Offset(0f, center - lineWidth / 2),
        size = Size(TIMELINE_BRANCH_LENGTH.toPx(), lineWidth),
    )
}

private val TIMELINE_WIDTH = 2.dp
private val TIMELINE_BRANCH_LENGTH = 22.dp
private val TIMELINE_ROW_GAP = 16.dp

@Preview(showBackground = true)
@Composable
private fun ReferralOnboardingScreenPreview() {
    ReferralOnboardingScreen(onGetStarted = {}, onBackClick = {})
}
