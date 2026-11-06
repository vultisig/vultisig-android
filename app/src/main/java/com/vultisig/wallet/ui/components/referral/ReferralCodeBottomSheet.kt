package com.vultisig.wallet.ui.components.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.bottomsheet.SettingsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralCodeBottomSheet(onContinue: () -> Unit, onDismissRequest: () -> Unit) {
    SettingsModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.v2.colors.backgrounds.surface1,
    ) {
        ReferralCodeBottomSheetContent(onContinue = onContinue)
    }
}

@Composable
internal fun ReferralCodeBottomSheetContent(onContinue: () -> Unit) {
    Column(
        modifier =
            Modifier.padding(horizontal = 35.dp)
                .padding(top = 6.dp, bottom = 12.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.referralcodeiphone),
            contentDescription = null,
            modifier = Modifier.size(width = 241.dp, height = 194.dp),
        )

        SequenceOfGradientText(
            listTextItems =
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.referral_invite_onboarding_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.referral_invite_onboarding_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.referral_invite_onboarding_3,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                ),
            style = Theme.brockmann.headings.title2,
        )

        Text(
            text = stringResource(R.string.referral_invite_sheet_description),
            style = Theme.brockmann.body.s.medium,
            textAlign = TextAlign.Center,
            color = Theme.v2.colors.text.secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        VsButton(
            label = stringResource(R.string.referral_onboarding_get_started),
            variant = Primary,
            state = Enabled,
            size = Medium,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
private fun ReferralCodeBottomSheetPreview() {
    ReferralCodeBottomSheet(onContinue = {}, onDismissRequest = {})
}
