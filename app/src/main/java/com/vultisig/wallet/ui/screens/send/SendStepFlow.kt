package com.vultisig.wallet.ui.screens.send

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.models.send.canNavigate
import com.vultisig.wallet.ui.models.send.dropsFocusLeaving
import com.vultisig.wallet.ui.models.send.isStepContinueDisabled
import com.vultisig.wallet.ui.models.send.stepState
import com.vultisig.wallet.ui.models.v3.onboarding.StepState
import com.vultisig.wallet.ui.screens.v2.home.components.rememberIsReduceMotionEnabled
import com.vultisig.wallet.ui.screens.v3.onboarding.StepIcon
import com.vultisig.wallet.ui.screens.v3.onboarding.StepSeparator
import com.vultisig.wallet.ui.theme.Theme

private val StepIconSize = 36.dp
private val StepGlyphSize = 20.dp
private val HeaderTopPadding = 8.dp
private val TitleTopPadding = 12.dp
private val TitleToScrollGap = 12.dp
private val ScrollEdgeFadeHeight = 12.dp
private val FloatingButtonGap = 16.dp
private val ContinueHorizontalPadding = 16.dp
private val ContinueVerticalPadding = 12.dp
private const val STEP_STATE_FADE_MS = 300
private const val STEP_POP_SCALE = 1.08f
private const val STEP_SLIDE_DURATION_MS = 300
private const val STEP_FADE_DURATION_MS = 200

/** Test tags of the step header icons; QA flows tap them. */
internal object SendStepTestTags {
    const val ASSET = "SendFormScreen.stepAsset"
    const val ADDRESS = "SendFormScreen.stepAddress"
    const val AMOUNT = "SendFormScreen.stepAmount"
}

private data class SendStep(
    val section: SendSections,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val title: Int,
    val testTag: String,
)

private val SendSteps =
    listOf(
        SendStep(
            section = SendSections.Asset,
            icon = R.drawable.ic_coins,
            label = R.string.form_token_selection_asset,
            title = R.string.send_step_choose_asset,
            testTag = SendStepTestTags.ASSET,
        ),
        SendStep(
            section = SendSections.Address,
            icon = R.drawable.ic_pin_filled,
            label = R.string.add_address_address_title,
            title = R.string.send_step_enter_address,
            testTag = SendStepTestTags.ADDRESS,
        ),
        SendStep(
            section = SendSections.Amount,
            icon = R.drawable.ic_calculator,
            label = R.string.send_amount,
            title = R.string.send_step_enter_amount,
            testTag = SendStepTestTags.AMOUNT,
        ),
    )

/**
 * The plain-send screen: a step header over one step at a time, with Continue floating over the
 * content. [pane] draws a step and applies `contentPadding` plus [StepBottomInsets] inside its
 * scroll, so its content clears the top fade and scrolls clear of the button and the keyboard.
 */
@Composable
internal fun SendStepFlowScreen(
    state: SendFormUiModel,
    onBackClick: () -> Unit,
    onSelectStep: (SendSections) -> Unit,
    onContinue: () -> Unit,
    onRefreshRequest: () -> Unit,
    pane: @Composable (step: SendSections, contentPadding: PaddingValues) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val density = LocalDensity.current
    var continueBlockHeightPx by remember { mutableIntStateOf(0) }
    var shownStep by remember { mutableStateOf(state.expandedSection) }

    LaunchedEffect(state.expandedSection) {
        val step = state.expandedSection
        if (step > shownStep) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (state.dropsFocusLeaving(shownStep)) focusManager.clearFocus()
        shownStep = step
    }

    Scaffold(
        topBar = {
            V2Topbar(title = stringResource(R.string.send_screen_title), onBackClick = onBackClick)
        },
        containerColor = V2Scaffold.CONTAINER_COLOR,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SendStepHeader(state = state, onSelectStep = onSelectStep)

                val contentPadding =
                    PaddingValues(
                        top = ScrollEdgeFadeHeight,
                        bottom = with(density) { continueBlockHeightPx.toDp() } + FloatingButtonGap,
                    )
                SendPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefreshRequest,
                    modifier = Modifier.padding(top = TitleToScrollGap).weight(1f),
                ) {
                    AnimatedContent(
                        targetState = state.expandedSection,
                        transitionSpec = stepSlideAndFadeSpec(isReduceMotion),
                        label = "send step",
                        modifier = Modifier.fillMaxSize(),
                    ) { step ->
                        Box(modifier = Modifier.fillMaxSize()) { pane(step, contentPadding) }
                    }

                    ScrollEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            FloatingContinue(
                isDisabled = state.isStepContinueDisabled(),
                clearsFocus = state.expandedSection == SendSections.Amount,
                onContinue = onContinue,
                onHeightChanged = { continueBlockHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Slide plus fade between steps: a later step comes in from the trailing edge, an earlier one from
 * the leading edge. With [isReduceMotion] it only crossfades.
 */
private fun stepSlideAndFadeSpec(
    isReduceMotion: Boolean
): AnimatedContentTransitionScope<SendSections>.() -> ContentTransform = {
    if (isReduceMotion) {
        fadeIn(tween(STEP_FADE_DURATION_MS)) togetherWith fadeOut(tween(STEP_FADE_DURATION_MS))
    } else {
        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
        val slide = tween<IntOffset>(STEP_SLIDE_DURATION_MS, easing = EaseInOut)
        val fade = tween<Float>(STEP_SLIDE_DURATION_MS, easing = EaseInOut)
        (slideInHorizontally(slide) { it * direction } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { -it * direction } + fadeOut(fade))
    }
}

/** Continue, floating above the navigation bar or the keyboard; reports its own height. */
@Composable
private fun FloatingContinue(
    isDisabled: Boolean,
    clearsFocus: Boolean,
    onContinue: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                // Measured inside the insets, so a keyboard animation never recomposes the step.
                .onSizeChanged { onHeightChanged(it.height) }
                .padding(horizontal = ContinueHorizontalPadding, vertical = ContinueVerticalPadding)
    ) {
        VsButton(
            label = stringResource(R.string.send_continue_button),
            state = if (isDisabled) VsButtonState.Disabled else VsButtonState.Enabled,
            onClick = {
                if (clearsFocus) focusManager.clearFocus()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The bottom insets a step's content scrolls clear of, under the floating Continue. */
internal val StepBottomInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.ime)

/** A step's scrolling column, inset by the screen gutter, [contentPadding] and the insets. */
@Composable
internal fun SendStepScrollPane(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .windowInsetsPadding(StepBottomInsets.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp),
        content = content,
    )
}

/** Dissolves content scrolled under the title instead of cutting it. */
@Composable
private fun ScrollEdgeFade(modifier: Modifier = Modifier) {
    val background = V2Scaffold.CONTAINER_COLOR
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ScrollEdgeFadeHeight)
                .background(Brush.verticalGradient(listOf(background, Color.Transparent)))
    )
}

@Composable
private fun SendStepHeader(state: SendFormUiModel, onSelectStep: (SendSections) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = HeaderTopPadding),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SendSteps.forEachIndexed { index, step ->
                if (index > 0) StepSeparator()
                SendStepButton(
                    step = step,
                    state = state.stepState(step.section),
                    isEnabled = state.canNavigate(step.section),
                    onClick = { onSelectStep(step.section) },
                )
            }
        }

        Crossfade(
            targetState = state.expandedSection,
            animationSpec = tween(STEP_STATE_FADE_MS, easing = EaseInOut),
            label = "send step title",
            modifier = Modifier.padding(top = TitleTopPadding),
        ) { section ->
            Text(
                text = stringResource(SendSteps.first { it.section == section }.title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SendStepButton(
    step: SendStep,
    state: StepState,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val scale = remember { Animatable(1f) }
    var wasDone by remember { mutableStateOf(state == StepState.Done) }
    LaunchedEffect(state) {
        val becameDone = state == StepState.Done && !wasDone
        wasDone = state == StepState.Done
        if (becameDone && !isReduceMotion) {
            scale.animateTo(STEP_POP_SCALE, spring(stiffness = Spring.StiffnessMedium))
            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    val label = stringResource(step.label)
    Crossfade(
        targetState = state,
        animationSpec = tween(if (isReduceMotion) 0 else STEP_STATE_FADE_MS, easing = EaseInOut),
        label = "send step state",
        modifier =
            Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .clickable(
                    enabled = isEnabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = label }
                .testTag(step.testTag),
    ) { shown ->
        StepIcon(logo = step.icon, state = shown, size = StepIconSize, iconSize = StepGlyphSize)
    }
}
