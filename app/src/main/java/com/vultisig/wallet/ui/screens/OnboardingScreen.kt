package com.vultisig.wallet.ui.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.models.OnboardingPages
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
            VsTopAppProgressBar(
                title = stringResource(R.string.onboarding_intro_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                progress = uiState.pageNumber + 1,
                total = uiState.pageTotal,
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
        if (!LocalInspectionMode.current) {
            RiveAnimation(
                animation = R.raw.onboarding,
                modifier = Modifier.fillMaxWidth(),
                onInit = { riveAnimationView ->
                    riveAnimationView.play(animationName = uiState.currentPage.animationName)
                }
            )
        } else {
            UiSpacer(24.dp)
        }
        Description(
            page = uiState.currentPage,
            modifier = Modifier
                .padding(horizontal = 24.dp)
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
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_2,
                        gradientColors = Theme.colors.gradients.primary,
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_3,
                        gradientColors = listOf(Theme.colors.text.light),
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
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_2,
                        gradientColors = Theme.colors.gradients.primary,
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_3,
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_4,
                        gradientColors = listOf(Theme.colors.text.primary),
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
                        gradientColors = Theme.colors.gradients.primary,
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_2,
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_3,
                        gradientColors = Theme.colors.gradients.primary,
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
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_4_part_2,
                        gradientColors = Theme.colors.gradients.primary,
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
                        gradientColors = Theme.colors.gradients.primary,
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_2,
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_3,
                        gradientColors = Theme.colors.gradients.primary,
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
                        gradientColors = listOf(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_6_part_2,
                        gradientColors = Theme.colors.gradients.primary,
                    ),
                ),
                style = Theme.brockmann.headings.title1,
                modifier = modifier
            )
        }
    }
}