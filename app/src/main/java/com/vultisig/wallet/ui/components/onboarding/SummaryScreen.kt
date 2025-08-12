package com.vultisig.wallet.ui.components.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rive.runtime.kotlin.RiveAnimationView
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SummaryScreen(
    checkState: Boolean,
    animationRes: Int,
    @StringRes buttonText: Int,
    onCheckChange: (Boolean) -> Unit,
    onButtonClicked: () -> Unit,
    onAnimationInit: (RiveAnimationView) -> Unit = {},
) {
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
                animation = animationRes,
                onInit = onAnimationInit
            )
        }
        VsCheckField(
            modifier = Modifier.padding(20.dp)
                .testTag("SummaryScreen.agree"),
            title = stringResource(id = R.string.onboarding_summary_check),
            isChecked = checkState,
            onCheckedChange = onCheckChange,
        )
        VsButton(
            onClick = onButtonClicked,
            label = stringResource(id = buttonText),
            state = if (checkState) VsButtonState.Enabled else VsButtonState.Disabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("SummaryScreen.continue")
        )
        UiSpacer(32.dp)
    }
}