package com.vultisig.wallet.ui.components.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsIconButton
import com.vultisig.wallet.ui.components.buttons.VsIconButtonSize
import com.vultisig.wallet.ui.components.buttons.VsIconButtonState
import com.vultisig.wallet.ui.components.buttons.VsIconButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import kotlinx.coroutines.delay

@Composable
internal fun OnboardingContent(
    state: OnboardingUiModel,
    paddingValues: PaddingValues,
    riveAnimation: Int,
    nextClick: () -> Unit,
    textDescription: @Composable (Int) -> Unit,
) {
    var buttonVisibility by remember { mutableStateOf(false) }
    var textVisibility by remember { mutableStateOf(false) }
    var currentPageIndex: Int by remember { mutableIntStateOf(state.pageIndex) }

    LaunchedEffect(state) {
        textVisibility = false
        buttonVisibility = false
        delay(1000)
        currentPageIndex = state.pageIndex
        textVisibility = true
        delay(1000)
        buttonVisibility = true
    }
    Box(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
    ) {
        RiveAnimation(
            animation = riveAnimation,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            onInit = { riveAnimationView ->
                riveAnimationView.setNumberState(
                    stateMachineName = ONBOARDING_STATE_MACHINE_NAME,
                    inputName = ONBOARDING_INPUT_NAME,
                    value = state.pageIndex.toFloat()
                )
            }
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 120.dp),
            visible = textVisibility,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            textDescription(
                currentPageIndex,
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            visible = buttonVisibility,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            VsIconButton(
                variant = VsIconButtonVariant.Primary,
                state = VsIconButtonState.Enabled,
                size = VsIconButtonSize.Medium,
                icon = R.drawable.ic_caret_right,
                onClick = nextClick,
                modifier = Modifier
                    .testTag(OnboardingContentTags.NEXT)
            )
        }
    }
}

private const val ONBOARDING_INPUT_NAME = "Index"
private const val ONBOARDING_STATE_MACHINE_NAME = "State Machine 1"


internal object OnboardingContentTags {
    const val NEXT = "OnboardingContent.next"
}