package com.vultisig.wallet.ui.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.buttons.VsIconButton
import com.vultisig.wallet.ui.components.rive.RiveAnimationPro
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.OnboardingState
import com.vultisig.wallet.ui.models.OnboardingViewModel
import com.vultisig.wallet.ui.theme.Theme


@ExperimentalAnimationApi
@Composable
internal fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    OnboardingScreen(
        uiState = uiState,
        onBackClick = {},
        onSkipClick = { viewModel.skip() },
        onNextClick = { viewModel.next() },
    )
}

@Composable
private fun OnboardingScreen(
    uiState: OnboardingState,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.onboarding_intro_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                actions = {
                    Text(
                        text = stringResource(R.string.welcome_screen_skip),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.extraLight,
                        modifier = Modifier.clickable { onSkipClick() }
                    )
                },
            )
        },
    ) { paddingValues ->
        OnboardingContent(
            uiState = uiState,
            paddingValues = paddingValues,
            nextClick = onNextClick,
        )
    }
}

@Composable
private fun OnboardingContent(
    uiState: OnboardingState,
    paddingValues: PaddingValues,
    nextClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RiveAnimationPro(
            resId = R.raw.onboarding,
            animationName = uiState.currentAnimation,
            contentDescription = null,
            update = { riveAnimationView ->
                riveAnimationView.play(animationName = uiState.currentAnimation)
            }
        )
        Spacer(
            modifier = Modifier
                .weight(0.3f)
        )

        VsIconButton(
            variant = Primary,
            state = Enabled,
            size = Medium,
            icon = R.drawable.ic_caret_right,
            onClick = nextClick,
        )

        UiSpacer(size = 24.dp)
    }
}