package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.RollingAnchor
import com.vultisig.wallet.ui.components.RollingText
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.amountInputTransformation
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.isContinueDisabled
import com.vultisig.wallet.ui.models.send.shouldFocusAmountField
import com.vultisig.wallet.ui.screens.v2.home.components.rememberIsReduceMotionEnabled
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Raises the keyboard only once the step has slid in. */
private val AmountFocusDelay = 350.milliseconds
private val AmountFieldMinHeight = 100.dp
// Room the token/fiat toggle takes on each side, so the amount stays centred on the card.
private val AmountSidePadding = 54.dp
private val AmountUnitSpacing = 8.dp
// Readable floor for the scaled gap, so a shrunken number never meets its unit.
private val MinAmountUnitSpacing = 4.dp
// Room for the caret, so a line that just fits does not scroll.
private val CaretAllowance = 4.dp
private val ChainIconSize = 16.dp
// Fits an 18-decimal amount with four whole digits and a six-letter ticker on a phone.
internal const val MIN_AMOUNT_SCALE = 0.36f
private const val AMOUNT_SCALE_DURATION_MS = 150
private const val UNIT_SWAP_HIDDEN_SCALE = 0.85f
private const val UNIT_SWAP_FADE_MS = 200
// SwiftUI's `.spring(duration: 0.35, bounce: 0.2)`: stiffness (2π / 0.35)² ≈ 322, damping 1 − 0.2.
private const val UNIT_SWAP_DAMPING = 0.8f
private const val UNIT_SWAP_STIFFNESS = 322f
private val SwitchInset = 4.dp
private val SwitchOptionSize = 32.dp
private val SwitchOptionGap = 2.dp
private val SwitchIconSize = 16.dp

/**
 * The plain-send Amount step as one card: chain, amount with the token/fiat toggle, inline
 * warnings, percentage chips, available balance, memo and destination tag, then the fee row.
 */
@Composable
internal fun SendAmountStep(
    state: SendFormUiModel,
    amountInputs: AmountInputs,
    optionalInputs: OptionalInputs,
    focusManager: FocusManager,
    onGasSettingsClick: () -> Unit,
    onSend: () -> Unit,
    onPendingFocusApplied: () -> Unit,
    isActive: Boolean,
) {
    val fiatFocusRequester = remember { FocusRequester() }
    // Read after the delay: the step may have been left while this pane slid in.
    val isActiveNow by rememberUpdatedState(isActive)
    // Focus once on arrival, after the slide, and once per request, on the block on screen.
    var hasArrived by remember { mutableStateOf(false) }
    LaunchedEffect(state.pendingFocusField) {
        val isRequested = state.pendingFocusField == SendFocusField.AMOUNT
        val isArrival = !hasArrived
        if (!shouldFocusAmountField(isActiveNow, isArrival, isRequested)) return@LaunchedEffect
        if (isArrival) delay(AmountFocusDelay)
        hasArrived = true
        if (!shouldFocusAmountField(isActiveNow, isArrival, isRequested)) return@LaunchedEffect
        val visibleField =
            if (state.usingTokenAmountInput) amountInputs.focusRequester else fiatFocusRequester
        visibleField.requestFocus()
        if (isRequested) onPendingFocusApplied()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.surface1,
                    shape = Theme.v2.radius.xl,
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                )
                .padding(all = 16.dp),
    ) {
        state.selectedCoin?.model?.address?.chain?.let { ChainLabel(it) }

        AmountEntry(
            state = state,
            amountInputs = amountInputs,
            fiatFocusRequester = fiatFocusRequester,
            isActive = isActive,
            onKeyboardAction = {
                // Same gate as Continue, so the keyboard cannot submit against a stale fee.
                if (!state.isContinueDisabled()) {
                    focusManager.clearFocus()
                    onSend()
                }
            },
        )

        ReapingErrorMessage(error = state.feeShortfallError ?: state.reapingError)

        AmountFractionChips(
            state = state,
            onChoosePercentageAmount = amountInputs.onChoosePercentageAmount,
            onChooseMaxTokenAmount = amountInputs.onChooseMaxTokenAmount,
        )

        BalanceAvailableRow(
            balance = state.selectedCoin?.balance ?: "0",
            ticker = state.selectedCoin?.title,
        )

        if (state.hasMemo || state.isDestinationTag) {
            Column {
                if (state.hasMemo) {
                    MemoSection(state = state, memoFieldState = optionalInputs.memoFieldState)
                }
                if (state.isDestinationTag) {
                    DestinationTagSection(
                        state = state,
                        destinationTagFieldState = optionalInputs.destinationTagFieldState,
                    )
                }
            }
        }

        if (state.showGasFee) {
            FadingHorizontalDivider(modifier = Modifier.fillMaxWidth())

            EstimatedNetworkFee(
                tokenGas = state.totalGas.asString(),
                fiatGas = state.estimatedFee.asString(),
                isLoading = state.isGasFeeLoading,
                onClick = onGasSettingsClick.takeIf { state.hasGasSettings },
            )
        }
    }
}

@Composable
private fun ChainLabel(chain: Chain) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(chain.logo),
            contentDescription = null,
            modifier = Modifier.size(ChainIconSize),
        )
        Text(
            text = chain.displayName,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

/** How far an amount block is shown (0 hidden, 1 shown) as its alpha and scale. */
internal data class AmountBlockAppearance(val alpha: Float, val scale: Float)

/** Outgoing and incoming blocks fade and scale about their centre; reduced motion only fades. */
internal fun amountBlockAppearance(shownFraction: Float, isReduceMotion: Boolean) =
    AmountBlockAppearance(
        alpha = shownFraction.coerceIn(0f, 1f),
        scale = if (isReduceMotion) 1f else lerp(UNIT_SWAP_HIDDEN_SCALE, 1f, shownFraction),
    )

/**
 * The asset and fiat amounts as two blocks stacked in one box, each with its own field. The toggle
 * crossfades and scales them; neither field's text changes, so no digit rolls.
 */
@Composable
private fun AmountEntry(
    state: SendFormUiModel,
    amountInputs: AmountInputs,
    fiatFocusRequester: FocusRequester,
    isActive: Boolean,
    onKeyboardAction: () -> Unit,
) {
    val ticker = state.selectedCoin?.title.orEmpty()
    val isReduceMotion = rememberIsReduceMotionEnabled()
    var isTokenShown by remember { mutableStateOf(state.usingTokenAmountInput) }
    LaunchedEffect(state.usingTokenAmountInput) { isTokenShown = state.usingTokenAmountInput }
    var hasAmountFocus by remember { mutableStateOf(false) }

    val tokenShownFraction by
        animateFloatAsState(
            targetValue = if (isTokenShown) 1f else 0f,
            animationSpec =
                if (isReduceMotion) tween(UNIT_SWAP_FADE_MS)
                else spring(dampingRatio = UNIT_SWAP_DAMPING, stiffness = UNIT_SWAP_STIFFNESS),
            label = "amount unit swap",
        )

    val selectUnit: (Boolean) -> Unit = { toToken ->
        if (toToken != isTokenShown) {
            isTokenShown = toToken
            // Focus moves straight to the other field, so the keyboard never closes in between.
            if (hasAmountFocus) {
                (if (toToken) amountInputs.focusRequester else fiatFocusRequester).requestFocus()
            }
            amountInputs.onToggleAmountInputType(toToken)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().heightIn(min = AmountFieldMinHeight)) {
        AmountBlock(
            isShown = isTokenShown,
            appearance = { amountBlockAppearance(tokenShownFraction, isReduceMotion) },
            modifier = Modifier.align(Alignment.Center),
        ) {
            ScalingAmountLine(
                fieldState = amountInputs.tokenAmountFieldState,
                unit = ticker,
                focusRequester = amountInputs.focusRequester,
                isShown = isTokenShown,
                isFocusable = { isActive && isTokenShown },
                onFocusChange = { hasAmountFocus = it },
                onLostFocus = amountInputs.onTokenAmountLostFocus,
                onKeyboardAction = onKeyboardAction,
                testTag = "SendFormScreen.amountField",
            )
            AmountCounterpart(amountInputs.fiatAmountFieldState, state.fiatCurrency)
        }

        AmountBlock(
            isShown = !isTokenShown,
            appearance = { amountBlockAppearance(1f - tokenShownFraction, isReduceMotion) },
            modifier = Modifier.align(Alignment.Center),
        ) {
            AmountCounterpart(amountInputs.tokenAmountFieldState, ticker)
            ScalingAmountLine(
                fieldState = amountInputs.fiatAmountFieldState,
                unit = state.fiatCurrency,
                focusRequester = fiatFocusRequester,
                isShown = !isTokenShown,
                isFocusable = { isActive && !isTokenShown },
                onFocusChange = { hasAmountFocus = it },
                onLostFocus = amountInputs.onTokenAmountLostFocus,
                onKeyboardAction = onKeyboardAction,
                testTag = "SendFormScreen.fiatAmountField",
            )
        }

        AmountUnitSwitch(
            isTokenSelected = isTokenShown,
            isReduceMotion = isReduceMotion,
            onSelect = selectUnit,
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(2f),
        )
    }
}

/** One amount block; hidden, it sits underneath and is left out of accessibility. */
@Composable
private fun AmountBlock(
    isShown: Boolean,
    appearance: () -> AmountBlockAppearance,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .padding(horizontal = AmountSidePadding)
                .zIndex(if (isShown) 1f else 0f)
                .graphicsLayer {
                    val (blockAlpha, blockScale) = appearance()
                    alpha = blockAlpha
                    scaleX = blockScale
                    scaleY = blockScale
                }
                .then(
                    if (isShown) Modifier
                    else Modifier.clearAndSetSemantics {}.pointerInput(Unit) { swallowTouches() }
                ),
        content = content,
    )
}

// The hidden block takes no touch, even where it reaches past the block on screen.
private suspend fun PointerInputScope.swallowTouches() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** The other unit's value, small, beside the amount being typed. */
@Composable
private fun AmountCounterpart(fieldState: TextFieldState, unit: String) {
    Text(
        text = "${fieldState.text.ifEmpty { "0" }} $unit",
        color = Theme.v2.colors.text.tertiary,
        style = Theme.brockmann.body.s.medium,
        maxLines = 1,
    )
}

/**
 * The asset / fiat switch: the icons stay put and the highlight slides to the chosen one on the
 * blocks' spring. It takes no focus, so a focused amount field can hand focus to the other.
 */
@Composable
private fun AmountUnitSwitch(
    isTokenSelected: Boolean,
    isReduceMotion: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightOffset by
        animateDpAsState(
            targetValue = if (isTokenSelected) 0.dp else SwitchOptionSize + SwitchOptionGap,
            animationSpec =
                if (isReduceMotion) snap()
                else spring(dampingRatio = UNIT_SWAP_DAMPING, stiffness = UNIT_SWAP_STIFFNESS),
            label = "amount unit highlight",
        )
    Box(
        modifier =
            modifier
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = CircleShape)
                .padding(all = SwitchInset)
    ) {
        Box(
            modifier =
                Modifier.offset { IntOffset(0, highlightOffset.roundToPx()) }
                    .size(SwitchOptionSize)
                    .background(color = Theme.v2.colors.primary.accent3, shape = CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(SwitchOptionGap)) {
            listOf(true to R.drawable.ic_coins, false to R.drawable.ic_dollar_sign).forEach {
                (isToken, icon) ->
                UiIcon(
                    drawableResId = icon,
                    size = SwitchIconSize,
                    tint = Theme.v2.colors.text.secondary,
                    modifier =
                        Modifier.size(SwitchOptionSize)
                            .clip(CircleShape)
                            .selectable(
                                selected = isToken == isTokenSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(isToken) },
                            )
                            .wrapContentSize(),
                )
            }
        }
    }
}

/**
 * The amount field and its unit on one centred line. A long amount shrinks the font of both, no
 * further than [MIN_AMOUNT_SCALE], before the field scrolls. Display only: the text is never
 * rewritten. While the line fits, the digits roll over the real field, which keeps the caret,
 * selection, paste and accessibility.
 */
@Composable
private fun ScalingAmountLine(
    fieldState: TextFieldState,
    unit: String,
    focusRequester: FocusRequester,
    isShown: Boolean,
    isFocusable: () -> Boolean,
    onFocusChange: (Boolean) -> Unit,
    onLostFocus: () -> Unit,
    onKeyboardAction: () -> Unit,
    testTag: String,
) {
    val baseStyle = Theme.brockmann.headings.largeTitle
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val text = fieldState.text.toString()
    val widths = rememberAmountLineWidths(text, unit, baseStyle)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availablePx = constraints.maxWidth.toFloat()
        val fitScale = widths.fitScale(availablePx)
        val scale by
            animateFloatAsState(
                targetValue = fitScale,
                animationSpec =
                    if (isReduceMotion) tween(0)
                    else tween(AMOUNT_SCALE_DURATION_MS, easing = EaseOut),
                label = "amount scale",
            )
        val style = baseStyle.copy(fontSize = baseStyle.fontSize * scale)
        // Only the shown block rolls; the hidden one draws the field's glyphs where they'd roll.
        val rollsDigits =
            isShown &&
                !isReduceMotion &&
                text.isNotEmpty() &&
                widths.lineWidth(MIN_AMOUNT_SCALE) <= availablePx
        var wasFocused by remember { mutableStateOf(false) }
        var fieldLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                state = fieldState,
                inputTransformation = amountInputTransformation(),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle =
                    style.copy(
                        color = if (rollsDigits) Color.Transparent else Theme.v2.colors.text.primary
                    ),
                cursorBrush = Theme.cursorBrush,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Send,
                    ),
                onKeyboardAction = { onKeyboardAction() },
                onTextLayout = { getResult -> fieldLayout = getResult() },
                modifier =
                    Modifier.weight(1f, fill = false)
                        .width(IntrinsicSize.Min)
                        .focusRequester(focusRequester)
                        .focusProperties { canFocus = isFocusable() }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                wasFocused = true
                            } else if (wasFocused) {
                                onLostFocus()
                            }
                            onFocusChange(focusState.isFocused)
                        }
                        .testTag(testTag),
                decorator = { textField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(text = "0", color = Theme.v2.colors.text.secondary, style = style)
                        } else if (rollsDigits) {
                            RolledAmount(text = text, style = style, fieldLayout = fieldLayout)
                        }
                        textField()
                    }
                },
            )

            UiSpacer(maxOf(AmountUnitSpacing * scale, MinAmountUnitSpacing))

            Text(text = unit, color = Theme.v2.colors.text.primary, style = style, maxLines = 1)
        }
    }
}

/** The full-size widths of [text] (or its "0" placeholder) and [unit], measured once per change. */
@Composable
private fun rememberAmountLineWidths(
    text: String,
    unit: String,
    style: TextStyle,
): AmountLineWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, unit, style, density) {
        with(density) {
            AmountLineWidths(
                textPx = measurer.measure(text.ifEmpty { "0" }, style).size.width.toFloat(),
                unitPx = measurer.measure(unit, style).size.width.toFloat(),
                gapPx = AmountUnitSpacing.toPx(),
                minGapPx = MinAmountUnitSpacing.toPx(),
                caretPx = CaretAllowance.toPx(),
            )
        }
    }
}

/** [text]'s digits rolling over the field, each placed at the field's own glyph position. */
@Composable
private fun RolledAmount(text: String, style: TextStyle, fieldLayout: TextLayoutResult?) {
    RollingText(
        text = text,
        color = Theme.v2.colors.text.primary,
        style = style,
        modifier = Modifier.clearAndSetSemantics {},
        anchor = RollingAnchor.Start,
        // A character the last layout has not placed yet sits at its end until the next one.
        charX =
            fieldLayout?.let { field ->
                { index ->
                    field
                        .getHorizontalPosition(
                            index.coerceAtMost(field.layoutInput.text.length),
                            usePrimaryDirection = true,
                        )
                        .roundToInt()
                }
            },
    )
}

/** The widths, at full size, that fit the amount line: number, unit, gap and caret room. */
internal data class AmountLineWidths(
    val textPx: Float,
    val unitPx: Float,
    val gapPx: Float,
    val minGapPx: Float,
    val caretPx: Float,
) {
    /** The line's width at [scale]: number and unit scale, the gap scales down to [minGapPx]. */
    fun lineWidth(scale: Float): Float =
        scale * (textPx + unitPx) + maxOf(scale * gapPx, minGapPx) + caretPx

    /**
     * The largest scale, from 1 down to [MIN_AMOUNT_SCALE], at which the line fits [availablePx].
     */
    fun fitScale(availablePx: Float): Float {
        if (lineWidth(1f) <= availablePx) return 1f
        val withScaledGap = (availablePx - caretPx) / (textPx + unitPx + gapPx)
        val scale =
            if (withScaledGap * gapPx >= minGapPx) withScaledGap
            else (availablePx - caretPx - minGapPx) / (textPx + unitPx)
        return scale.coerceIn(MIN_AMOUNT_SCALE, 1f)
    }
}

/** The collapsible memo field; it opens for a prefilled memo and while the memo is too long. */
@Composable
private fun MemoSection(state: SendFormUiModel, memoFieldState: TextFieldState) {
    // Open when the form starts with a memo (e.g. prefilled by a send deeplink), so the
    // user sees what will be signed.
    var isMemoExpanded by remember { mutableStateOf(memoFieldState.text.isNotEmpty()) }

    // Reveal the field while the memo is over the chain's limit: the inline error is
    // the only visible reason Continue is disabled.
    LaunchedEffect(state.memoError) { if (state.memoError != null) isMemoExpanded = true }

    val rotationAngle by
        animateFloatAsState(
            targetValue = if (isMemoExpanded) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "caretRotation",
        )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clickable {
                    // Collapsing while the memo is over the limit would hide that
                    // error, so it stays open until the memo fits.
                    isMemoExpanded = state.memoError != null || !isMemoExpanded
                }
                .padding(vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.send_form_add_memo),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )

        UiIcon(
            drawableResId = R.drawable.ic_caret_down,
            tint = Theme.v2.colors.text.primary,
            size = 16.dp,
            modifier = Modifier.rotate(rotationAngle),
        )
    }

    UiSpacer(12.dp)

    AnimatedVisibility(visible = isMemoExpanded) {
        val clipboardData = VsClipboardService.getClipboardData()
        Column {
            VsTextInputField(
                textFieldState = memoFieldState,
                hint = stringResource(R.string.send_form_enter_memo),
                autoCorrectEnabled = false,
                innerState =
                    if (state.memoError != null) VsTextInputFieldInnerState.Error
                    else VsTextInputFieldInnerState.Default,
                footNote = state.memoError?.asString(),
                trailingIcon = R.drawable.paste,
                onTrailingIconClick = {
                    clipboardData.value
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { memoFieldState.setTextAndPlaceCursorAtEnd(text = it) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(12.dp)
        }
    }
}

/**
 * The XRP destination-tag field below the memo. Locked when auto-filled from a pasted X-address,
 * which also opens it so the value is visible.
 */
@Composable
private fun DestinationTagSection(
    state: SendFormUiModel,
    destinationTagFieldState: TextFieldState,
) {
    var isTagExpanded by remember { mutableStateOf(destinationTagFieldState.text.isNotEmpty()) }

    // Reveal the field when an X-address paste locks/fills the tag.
    LaunchedEffect(state.destinationTagLocked) {
        if (state.destinationTagLocked) isTagExpanded = true
    }

    val tagRotationAngle by
        animateFloatAsState(
            targetValue = if (isTagExpanded) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "tagCaretRotation",
        )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { isTagExpanded = !isTagExpanded }.padding(vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.send_form_add_destination_tag),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )

        UiIcon(
            drawableResId = R.drawable.ic_caret_down,
            tint = Theme.v2.colors.text.primary,
            size = 16.dp,
            modifier = Modifier.rotate(tagRotationAngle),
        )
    }

    UiSpacer(12.dp)

    AnimatedVisibility(visible = isTagExpanded) {
        val tagClipboardData = VsClipboardService.getClipboardData()
        Column {
            VsTextInputField(
                textFieldState = destinationTagFieldState,
                hint = stringResource(R.string.send_form_enter_destination_tag),
                keyboardType = KeyboardType.Number,
                autoCorrectEnabled = false,
                enabled = !state.destinationTagLocked,
                trailingIcon = R.drawable.paste.takeIf { !state.destinationTagLocked },
                onTrailingIconClick = {
                    tagClipboardData.value
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { destinationTagFieldState.setTextAndPlaceCursorAtEnd(text = it) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(12.dp)
        }
    }
}
