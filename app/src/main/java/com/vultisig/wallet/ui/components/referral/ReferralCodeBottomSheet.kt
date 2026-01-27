package com.vultisig.wallet.ui.components.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReferralCodeBottomSheet(
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        ReferralCodeBottomSheetContent(
            onContinue = onContinue,
        )
    }
}

@Composable
internal fun ReferralCodeBottomSheetContent(
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.referralcodeiphone),
                contentDescription = "ReferralImage",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        UiSpacer(32.dp)

        SequenceOfGradientText(
            listTextItems = listOf(
                PartiallyGradientTextItem(
                    resId = R.string.referral_invite_onboarding_1,
                    coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                ),
                PartiallyGradientTextItem(
                    resId = R.string.referral_invite_onboarding_2,
                    coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                ),
                PartiallyGradientTextItem(
                    resId = R.string.referral_invite_onboarding_3,
                    coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                ),
            ),
            style = Theme.brockmann.headings.title2,
        )

        UiSpacer(32.dp)

        Text(
            text = stringResource(R.string.referral_invite_sheet_description),
            style = Theme.brockmann.body.s.medium,
            textAlign = TextAlign.Center,
            color = Theme.colors.text.light,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        )

        UiSpacer(32.dp)

        VsButton(
            label = stringResource(R.string.referral_invite_next),
            variant = Primary,
            state = Enabled,
            size = Medium,
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
private fun ReferralCodeBottomSheetPreview() {
    ReferralCodeBottomSheet(
        onContinue = {},
        onDismissRequest = {},
    )
}