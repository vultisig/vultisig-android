package com.vultisig.wallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.buttons.VsIconButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppProgressBar
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.models.OnboardingPages
import com.vultisig.wallet.ui.models.OnboardingUiModel
import com.vultisig.wallet.ui.models.OnboardingViewModel
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.delay

@Composable
internal fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingScreen(
        state = state,
        onBackClick = {},
        onSkipClick = viewModel::skip,
        onNextClick = viewModel::next,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiModel,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppProgressBar(
                title = stringResource(R.string.onboarding_intro_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                progress = state.pageIndex + 1,
                total = state.pageTotal,
                actions = {
                    Text(
                        text = stringResource(R.string.welcome_screen_skip),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.extraLight,
                        modifier = Modifier.clickable(onClick = onSkipClick)
                    )
                },
            )
        },
    ) { paddingValues ->
        OnboardingContent(
            state = state,
            paddingValues = paddingValues,
            nextClick = onNextClick,
        )
    }
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiModel,
    paddingValues: PaddingValues,
    nextClick: () -> Unit,
) {
    var buttonVisibility by remember { mutableStateOf(false) }
    var textVisibility by remember { mutableStateOf(false) }
    var currentPageText: OnboardingPages  by remember { mutableStateOf(OnboardingPages.Screen1) }

    LaunchedEffect(state) {
        textVisibility = false
        buttonVisibility = false
        delay(1000)
        currentPageText = state.currentPage
        textVisibility = true
        delay(1000)
        buttonVisibility = true
    }
    Box(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
    ) {
        if (!LocalInspectionMode.current) {
            RiveAnimation(
                animation = R.raw.onboarding_v2,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                onInit = { riveAnimationView ->
                    riveAnimationView.play(animationName = state.currentPage.animationName)
                }
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 120.dp),
            visible = textVisibility,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Description(
                page = currentPageText,
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
                variant = Primary,
                state = Enabled,
                size = Medium,
                icon = R.drawable.ic_caret_right,
                onClick = nextClick,
            )
        }
    }
}

@Composable
private fun Description(
    page: OnboardingPages,
    modifier: Modifier = Modifier,
) {
    when (page) {
        is OnboardingPages.Screen1 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_3,
                        coloring = GradientColoring.VsColor(Theme.colors.text.light),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
        is OnboardingPages.Screen2 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_3,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_4,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
        is OnboardingPages.Screen3 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_1,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_2,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_3,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
        is OnboardingPages.Screen4 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_4_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_4_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
        is OnboardingPages.Screen5 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_1,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_2,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_3,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
        is OnboardingPages.Screen6 -> {
            SequenceOfGradientText(
                listTextItems = listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_6_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_6_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
    }
}