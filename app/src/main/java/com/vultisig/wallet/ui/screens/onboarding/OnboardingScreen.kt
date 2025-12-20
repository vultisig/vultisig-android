package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.onboarding.OnboardingContent
import com.vultisig.wallet.ui.components.topbar.VsTopAppBarAction
import com.vultisig.wallet.ui.components.topbar.VsTopAppProgressBar
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.onboarding.OnboardingViewModel
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.delay

@Composable
internal fun OnboardingScreen(
    model: OnboardingViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    var showPreview by remember { mutableStateOf(true) }
    var fadeoutPreviewText by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        fadeoutPreviewText = true
        delay(200)
        showPreview = false
    }
    if (showPreview) {
        Box(
            modifier = Modifier
                .background(Theme.v2.colors.backgrounds.primary)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                modifier = Modifier.padding(64.dp),
                visible = !fadeoutPreviewText,
                enter = slideInVertically(),
                exit = fadeOut(tween(200)),
            ) {
                SequenceOfGradientText(
                    listTextItems = listOf(
                        PartiallyGradientTextItem(
                            resId = R.string.onboarding_intro_preview_part_1,
                            coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                        ),
                        PartiallyGradientTextItem(
                            resId = R.string.onboarding_intro_preview_part_2,
                            coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                        ),
                    ),
                    style = Theme.brockmann.headings.title1,
                )
            }
        }
    } else {
        OnboardingScreen(
            state = state,
            onBackClick = model::back,
            onSkipClick = model::skip,
            onNextClick = model::next,
        )
    }
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiModel,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    V2Scaffold(
        applyDefaultPaddings = true,
        applyScaffoldPaddings = true,
        topBar = {
            VsTopAppProgressBar(
                navigationContent = {
                    Row(
                        Modifier.clickable(onClick = onBackClick),
                    ) {
                        VsTopAppBarAction(
                            icon = R.drawable.ic_caret_left,
                            onClick = onBackClick,
                        )
                        UiSpacer(
                            size = 8.dp
                        )
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = stringResource(R.string.onboarding_intro_back),
                            style = Theme.brockmann.headings.title3,
                            color = Theme.v2.colors.text.primary,
                            textAlign = TextAlign.Start,
                        )
                    }
                },
                progress = state.pageIndex + 1,
                total = state.pageTotal,
                actions = {
                    Text(
                        text = stringResource(R.string.welcome_screen_skip),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.extraLight,
                        modifier = Modifier
                            .clickable(onClick = onSkipClick)
                            .testTag("OnboardingScreen.skip")
                    )
                },
            )
        },
        content = {
            OnboardingContent(
                state = state,
                riveAnimation = R.raw.riv_onboarding,
                nextClick = onNextClick,
                textDescription = { index ->
                    Description(index = index)
                },
            )
        }
    )
}

@Composable
private fun Description(
    index: Int,
    modifier: Modifier = Modifier,
) {
    SequenceOfGradientText(
        listTextItems = when (index) {
            0 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_3,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.light),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_1_part_4,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )
            }

            1 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_3,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_2_part_4,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )

            }

            2 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_1,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_2,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_3_part_3,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )

            }

            3 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_4_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_4_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )
            }

            4 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_1,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_2,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_5_part_3,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )
            }

            else -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_6_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_desc_page_6_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )
            }
        },
        style = Theme.brockmann.headings.title1,
        modifier = modifier
    )
}