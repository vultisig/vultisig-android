package com.vultisig.wallet.ui.screens.swap

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.utils.timerFlow
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsBasicTextField
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.library.form.FormDetails2
import com.vultisig.wallet.ui.components.rememberKeyboardVisibilityAsState
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.components.fastselection.contentWithFastSelection
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.components.util.toPx
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import kotlin.time.Duration

internal fun NavGraphBuilder.swapScreen(
    navController: NavHostController,
) {
    contentWithFastSelection<Route.Swap.SwapMain, Route.Swap>(
        navController = navController
    ) { onDragStart, onDrag, onDragEnd ->

        val model: SwapFormViewModel = hiltViewModel()
        val state by model.uiState.collectAsState()

        SwapScreen(
            state = state,
            srcAmountTextFieldState = model.srcAmountState,
            onBackClick = model::back,
            onSwap = model::swap,
            onSelectSrcNetworkClick = model::selectSrcNetwork,
            onSelectSrcToken = model::selectSrcToken,
            onSelectDstNetworkClick = model::selectDstNetwork,
            onDismissError = model::hideError,
            onSelectDstToken = model::selectDstToken,
            onFlipSelectedTokens = model::flipSelectedTokens,
            onSelectSrcPercentage = model::selectSrcPercentage,

            onDragStart = onDragStart,
            onDragCancel = onDragEnd,
            onDragEnd = onDragEnd,
            onDrag = onDrag,
            onDstLongPressStarted = model::selectDstNetworkPopup,
            onSrcLongPressStarted = model::selectSrcNetworkPopup,
        )
    }
}


@Composable
internal fun SwapScreen(
    state: SwapFormUiModel,
    srcAmountTextFieldState: TextFieldState,
    onBackClick: () -> Unit = {},
    onSelectSrcNetworkClick: () -> Unit = {},
    onSelectSrcToken: () -> Unit = {},
    onSelectDstNetworkClick: () -> Unit = {},
    onSelectDstToken: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onFlipSelectedTokens: () -> Unit = {},
    onSwap: () -> Unit = {},
    onSelectSrcPercentage: (Float) -> Unit = {},

    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDstLongPressStarted: (Offset) -> Unit = {},
    onSrcLongPressStarted: (Offset) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    val interactionSource = remember { MutableInteractionSource() }
    val isSrcAmountFocused by interactionSource.collectIsFocusedAsState()

    val isShowingKeyboard by rememberKeyboardVisibilityAsState()

    var isFeeDetailsExpanded by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isFeeDetailsExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "caretRotation"
    )

    VsScaffold(
        title = stringResource(R.string.chain_account_view_swap),
        onBackClick = onBackClick,
        actions = {
            if (state.expiredAt != null) {
                QuoteTimer(
                    expiredAt = state.expiredAt,
                )
            }
        },
        content = {

            var topCenter by remember {
                mutableStateOf(Offset.Zero)
            }
            var bottomCenter by remember {
                mutableStateOf(Offset.Zero)
            }
            val space = 8.dp

            var flipButtonBottomCenter by remember {
                mutableStateOf(Offset.Zero)
            }

            val error = state.error ?: state.formError

            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(space)
                        ) {
                            Box {
                                val rotation = remember { Animatable(0f) }

                                // Trigger spin when this is incremented
                                var spinTrigger by remember { mutableIntStateOf(0) }

                                // Launch the animation every time trigger changes
                                LaunchedEffect(spinTrigger) {
                                    rotation.snapTo(0f)
                                    rotation.animateTo(
                                        targetValue = 180f,
                                        animationSpec = tween(
                                            durationMillis = 600,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }

                                TokenInput(
                                    isLoading = state.isLoading,
                                    title = stringResource(R.string.swap_form_from_title),
                                    selectedToken = state.selectedSrcToken,
                                    fiatValue = state.srcFiatValue,
                                    onSelectNetworkClick = onSelectSrcNetworkClick,
                                    onSelectTokenClick = onSelectSrcToken,
                                    shape = RoundedWithCutoutShape(
                                        cutoutPosition = CutoutPosition.Bottom,
                                        cutoutOffsetY = -space / 2,
                                        cutoutRadius = 28.dp,
                                        onCircleBoundsChanged = {
                                            topCenter = it
                                        }
                                    ),
                                    focused = true,
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    onDragStart = onDragStart,
                                    onLongPressStarted = onSrcLongPressStarted,
                                    textFieldContent = {
                                        VsBasicTextField(
                                            textFieldState = srcAmountTextFieldState,
                                            style = Theme.brockmann.headings.title2,
                                            color = Theme.colors.text.light,
                                            textAlign = TextAlign.End,
                                            hint = "0",
                                            hintColor = Theme.colors.text.extraLight,
                                            hintStyle = Theme.brockmann.headings.title2,
                                            lineLimits = TextFieldLineLimits.SingleLine,
                                            interactionSource = interactionSource,
                                            // TODO onAmountLostFocus
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        )
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            this.translationY = -size.height / 2 + topCenter.y
                                            this.translationX =
                                                (topCenter.x + bottomCenter.x).div(2) - (size.width) / 2
                                        }
                                        .size(40.dp)
                                        .background(
                                            color = if (error != null) Theme.colors.alerts.error else Theme.colors.buttons.tertiary,
                                            shape = CircleShape,
                                        )
                                        .padding(all = space)
                                        .onGloballyPositioned {
                                            flipButtonBottomCenter =
                                                it.boundsInParent().bottomCenter
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = state.isLoading || state.isLoadingNextScreen,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(150)) togetherWith
                                                    fadeOut(animationSpec = tween(150))
                                        },
                                    ) { isLoading ->
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Theme.colors.text.primary,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(
                                                    id = if (error == null) R.drawable.ic_arrow_bottom_top
                                                    else R.drawable.iconwarning
                                                ),
                                                contentDescription = null,
                                                tint = Theme.colors.text.primary,
                                                modifier = Modifier
                                                    .clickable {
                                                        spinTrigger++
                                                        onFlipSelectedTokens()
                                                    }
                                                    .size(24.dp)
                                                    .graphicsLayer {
                                                        rotationZ =
                                                            if (error == null) rotation.value else 0f
                                                    },
                                            )
                                        }
                                    }
                                }
                            }

                            TokenInput(
                                title = stringResource(R.string.swap_form_dst_token_title),
                                isLoading = state.isLoading,
                                selectedToken = state.selectedDstToken,
                                fiatValue = state.estimatedDstFiatValue,
                                onSelectNetworkClick = onSelectDstNetworkClick,
                                onSelectTokenClick = onSelectDstToken,
                                shape = RoundedWithCutoutShape(
                                    cutoutPosition = CutoutPosition.Top,
                                    cutoutOffsetY = -space / 2,
                                    cutoutRadius = 28.dp,
                                    onCircleBoundsChanged = {
                                        bottomCenter = it
                                    }
                                ),
                                onDrag = onDrag,
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                                onDragStart = onDragStart,
                                onLongPressStarted = onDstLongPressStarted,
                                focused = false,
                                textFieldContent = {
                                    Text(
                                        text = state.estimatedDstTokenValue,
                                        style = Theme.brockmann.headings.title2,
                                        color = Theme.colors.text.light,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                    )
                                }
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(
                                horizontal = 8.dp,
                            )
                    ) {
                        val placeHolderModifier = Modifier
                            .height(16.dp)
                            .width(80.dp)
                        FormDetails2(
                            title = stringResource(R.string.swap_screen_provider_title),
                            value = state.provider.asString(),
                        )

                        FormDetails2(
                            modifier = Modifier
                                .clickable(onClick = {
                                    isFeeDetailsExpanded = !isFeeDetailsExpanded
                                }),
                            title = stringResource(R.string.swap_form_total_fees_title),
                            valueComposable = if (state.isLoading) {
                                {
                                    UiPlaceholderLoader(placeHolderModifier)
                                }
                            } else {
                                {
                                    Row {
                                        Text(
                                            text = state.totalFee,
                                            color = Theme.colors.text.light,
                                            style = Theme.brockmann.supplementary.caption,
                                            textAlign = TextAlign.End,
                                        )

                                        UiSpacer(size = 8.dp)
                                        UiIcon(
                                            drawableResId = R.drawable.ic_caret_down,
                                            tint = Theme.colors.text.primary,
                                            size = 16.dp,
                                            modifier = Modifier
                                                .rotate(rotationAngle)
                                        )
                                    }
                                }
                            },
                        )

                        AnimatedVisibility(visible = isFeeDetailsExpanded && state.isLoading.not()) {
                            Row(
                                modifier = Modifier
                                    .height(IntrinsicSize.Max)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .fillMaxHeight()
                                        .background(
                                            color = Theme.colors.border.primaryAccent4,
                                            shape = CircleShape
                                        )
                                )

                                UiSpacer(
                                    size = 8.dp
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {

                                    FormDetails2(
                                        modifier = Modifier.fillMaxWidth(),
                                        title = buildAnnotatedString {
                                            append(stringResource(R.string.swap_form_gas_title))
                                        },
                                        value = buildAnnotatedString {
                                            withStyle(
                                                style = SpanStyle(
                                                    color = Theme.colors.neutrals.n100,
                                                )
                                            ) {
                                                append(state.networkFee)
                                            }
                                            append(" ")
                                            withStyle(
                                                style = SpanStyle(
                                                    color = Theme.colors.neutrals.n400,
                                                )
                                            ) {
                                                append(
                                                    if (state.networkFeeFiat.isNotEmpty())
                                                        "(~${state.networkFeeFiat})"
                                                    else ""
                                                )
                                            }
                                        },
                                        placeholder = if (state.isLoading) {
                                            { UiPlaceholderLoader(placeHolderModifier) }
                                        } else null
                                    )

                                    FormDetails2(
                                        title = stringResource(R.string.swap_form_estimated_fees_title),
                                        value = state.fee,
                                        placeholder = if (state.isLoading) {
                                            { UiPlaceholderLoader(placeHolderModifier) }
                                        } else null
                                    )

                                    if(state.vultBpsDiscount != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            VultDiscountTier(
                                                vultBpsDiscount = state.vultBpsDiscount,
                                                tierType = state.tierType
                                            )

                                            Text(
                                                text = "-${state.vultBpsDiscountFiatValue}",
                                                color = Theme.colors.text.light,
                                                style = Theme.brockmann.supplementary.caption,
                                            )
                                        }
                                    }

                                    if(state.referralBpsDiscount != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {

                                            UiIcon(
                                                drawableResId = R.drawable.referral_code,
                                                size = 16.dp,
                                                tint = Theme.colors.border.primaryAccent4,
                                            )
                                            UiSpacer(
                                                size = 4.dp
                                            )

                                            Text(
                                                text = stringResource(
                                                    R.string.swap_form_referral_discount_bps,
                                                    state.referralBpsDiscount
                                                ),
                                                color = Theme.colors.text.extraLight,
                                                style = Theme.brockmann.supplementary.caption,
                                            )

                                            UiSpacer(
                                                weight = 1f
                                            )

                                            Text(
                                                text = "-${state.referralBpsDiscountFiatValue}",
                                                color = Theme.colors.text.light,
                                                style = Theme.brockmann.supplementary.caption,
                                            )
                                        }
                                    }



                                }
                            }
                        }

                    }
                }


                error?.let {
                    val errorBoxWidth = 200.dp
                    val errorWidthBoxPx = errorBoxWidth.toPx().toInt()
                    val spacePx = space.toPx().toInt()
                    HintBox(
                        modifier = Modifier.width(errorBoxWidth),
                        message = error.asString(),
                        onDismissClick = onDismissError,
                        title = stringResource(R.string.dialog_default_error_title),
                        offset = IntOffset(
                            x = flipButtonBottomCenter.x.toInt() - errorWidthBoxPx.div(2),
                            y = flipButtonBottomCenter.y.toInt() + spacePx
                        ),
                        isVisible = true,
                    )
                }
            }

        },
        bottomBar = {
            AnimatedContent(
                targetState = isSrcAmountFocused && isShowingKeyboard,
                transitionSpec = {
                    val animationSpec = tween<IntOffset>(durationMillis = 60)
                    slideInVertically(animationSpec) { it } togetherWith
                            slideOutVertically(animationSpec) { it }
                }
            ) { showPercentagePicker ->
                if (showPercentagePicker) {
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Theme.colors.border.light
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Theme.colors.backgrounds.secondary,
                                )
                                .padding(
                                    vertical = 12.dp,
                                    horizontal = 8.dp,
                                ),
                        ) {
                            PercentageItem(
                                title = "25%",
                                onClick = {
                                    onSelectSrcPercentage(0.25f)
                                },
                            )
                            PercentageItem(
                                title = "50%",
                                onClick = {
                                    onSelectSrcPercentage(0.5f)
                                },
                            )
                            PercentageItem(
                                title = "75%",
                                onClick = {
                                    onSelectSrcPercentage(0.75f)
                                },
                            )
                            if (state.enableMaxAmount)
                                PercentageItem(
                                    title = "MAX",
                                    onClick = {
                                        onSelectSrcPercentage(1f)
                                    },
                                )
                        }
                    }
                } else {
                    VsButton(
                        label = if (state.isLoading) {
                            stringResource(R.string.swap_swap_button_fill_in_amount)
                        } else {
                            stringResource(R.string.swap_swap_button)
                        },
                        variant = VsButtonVariant.Primary,
                        state = if (state.isSwapDisabled || state.isLoading) {
                            VsButtonState.Disabled
                        } else {
                            VsButtonState.Enabled
                        },
                        onClick = {
                            focusManager.clearFocus(true)
                            onSwap()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 12.dp,
                                horizontal = 24.dp,
                            )
                    )
                }
            }
        }
    )
}

@Composable
private fun RowScope.PercentageItem(
    title: String,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = Theme.colors.backgrounds.tertiary_2,
                shape = RoundedCornerShape(99.dp),
            )
            .padding(
                all = 8.dp,
            )
            .weight(1f)
    )
}

@Composable
private fun VultDiscountTier(vultBpsDiscount: Int, tierType: TierType?) {
    val (title, logo) = when (tierType) {
        TierType.BRONZE -> R.string.vault_tier_bronze to R.drawable.type_bronze_tier__size_small
        TierType.SILVER -> R.string.vault_tier_silver to R.drawable.type_silver_tier__size_small
        TierType.GOLD -> R.string.vault_tier_gold to R.drawable.type_gold_tier__size_small
        TierType.PLATINUM -> R.string.vault_tier_platinum to R.drawable.type_platinum_tier__size_small
        TierType.DIAMOND -> R.string.vault_tier_diamond to R.drawable.type_diamond__size_small
        TierType.ULTIMATE -> R.string.vault_tier_ultimate to R.drawable.tier_ultimate
        else -> null to null
    }

    if (title == null || logo == null)
        return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        val infiniteTransition = rememberInfiniteTransition(label = "rotation")

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Image(
            painterResource(logo),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation)
        )

        Text(
            text = stringResource(
                R.string.swap_form_vult_discount_bps,
                stringResource(title),
                vultBpsDiscount,
            ),
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

@Composable
private fun TokenInput(
    isLoading: Boolean,
    title: String,
    selectedToken: TokenBalanceUiModel?,
    fiatValue: String,
    onSelectNetworkClick: () -> Unit,
    onSelectTokenClick: () -> Unit,
    shape: Shape,
    focused: Boolean,
    modifier: Modifier = Modifier,
    @SuppressLint("ComposableLambdaParameterNaming")
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
    textFieldContent: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .background(
                color = if (focused)
                    Theme.colors.backgrounds.secondary
                else
                    Theme.colors.backgrounds.disabled,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Theme.colors.border.light,
                shape = shape,
            )
            .clip(shape)
            .padding(
                all = 16.dp,
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val selectedChain = selectedToken?.model?.address?.chain

            if (selectedChain != null) {
                ChainSelector(
                    title = title,
                    chain = selectedChain,
                    onClick = onSelectNetworkClick,
                    onDragStart = onDragStart,
                    onDragCancel = onDragCancel,
                    onDragEnd = onDragEnd,
                    onDrag = onDrag,
                    onLongPressStarted = onLongPressStarted,
                )
            }

            Text(
                text = selectedToken?.let { "${it.balance} ${it.title}" } ?: "",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
                textAlign = TextAlign.End,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TokenChip(
                selectedToken = selectedToken,
                onSelectTokenClick = onSelectTokenClick,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isLoading && title == stringResource(R.string.swap_form_dst_token_title)) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .height(24.dp)
                            .width(150.dp)
                    )
                } else {
                    textFieldContent()
                }

                if (isLoading) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .height(16.dp)
                            .width(80.dp)
                    )
                } else {
                    Text(
                        text = fiatValue,
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.colors.text.extraLight,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TokenChip(
    selectedToken: TokenBalanceUiModel?,
    onSelectTokenClick: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
) {

    var fieldPosition by remember { mutableStateOf(Offset.Zero) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onSelectTokenClick)
            .onGloballyPositioned { coordinates ->
                fieldPosition = coordinates.positionInWindow()
            }
            .pointerInput(Unit) {

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val screenPosition = Offset(
                            x = fieldPosition.x + offset.x,
                            y = fieldPosition.y + offset.y
                        )
                        onDragStart(screenPosition)
                        onLongPressStarted(screenPosition)
                    },
                    onDrag = { change: PointerInputChange, _ ->
                        val localPos = change.position
                        val screenPos = Offset(
                            x = fieldPosition.x + localPos.x,
                            y = fieldPosition.y + localPos.y
                        )
                        onDrag(screenPos)
                        change.consume()
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .background(
                color = Theme.colors.backgrounds.tertiary_2,
                shape = RoundedCornerShape(99.dp)
            )
            .padding(
                all = 6.dp,
            )

    ) {
        TokenLogo(
            errorLogoModifier = Modifier
                .size(32.dp)
                .background(Theme.colors.neutrals.n100),
            logo = selectedToken?.tokenLogo ?: "",
            title = selectedToken?.title ?: "",
            modifier = Modifier
                .size(32.dp)
        )

        UiSpacer(8.dp)

        Column {
            Text(
                text = selectedToken?.title ?: "",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.primary,
            )

            if (selectedToken?.isNativeToken == true) {
                Text(
                    text = stringResource(R.string.swap_form_native),
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.colors.text.extraLight,
                )
            }
        }

        UiSpacer(4.dp)

        UiIcon(
            drawableResId = R.drawable.ic_chevron_right_small,
            size = 20.dp,
            tint = Theme.colors.text.primary,
        )

        UiSpacer(6.dp)
    }
}

@Composable
private fun QuoteTimer(
    expiredAt: Instant,
    modifier: Modifier = Modifier,
) {
    var timeLeft: String by remember { mutableStateOf("") }
    var progress: Float by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(expiredAt) {
        timerFlow()
            .collect {
                val now = Clock.System.now()
                val left = expiredAt - now
                timeLeft = formatDurationAsMinutesSeconds(left)
                progress = (left / expiredAfter).toFloat()
            }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(99.dp)
            )
            .padding(
                vertical = 6.dp,
                horizontal = 12.dp,
            )
    ) {
        Text(
            text = timeLeft,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.light,
        )

        CircularProgressIndicator(
            progress = { progress },
            trackColor = Theme.colors.border.normal,
            color = Theme.colors.primary.accent4,
            strokeCap = StrokeCap.Square,
            strokeWidth = 2.dp,
            gapSize = 0.dp,
            modifier = Modifier
                .size(16.dp)
        )
    }
}

private fun formatDurationAsMinutesSeconds(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Preview
@Composable
internal fun SwapFormScreenPreview() {
    SwapScreen(
        state = SwapFormUiModel(
            estimatedDstTokenValue = "0",
        ),
        srcAmountTextFieldState = TextFieldState(),
    )
}

@Preview
@Composable
internal fun SwapFormScreenPreview2() {
    SwapScreen(
        state = SwapFormUiModel(
            selectedSrcToken = longTokenInput,
            selectedDstToken = tokenInput,
            srcFiatValue = "5.25",
            estimatedDstTokenValue = "12.80",
            estimatedDstFiatValue = "5.24",
            provider = UiText.DynamicString("ThorSwap"),
            networkFee = "0.02 RUNE",
            networkFeeFiat = "0.004 USD",
            totalFee = "0.024 USD",
            fee = "0.02 RUNE",
            error = null,
            formError = null,
            isSwapDisabled = false,
            isLoading = false,
            isLoadingNextScreen = false,
            expiredAt = Clock.System.now()
        ),
        srcAmountTextFieldState = TextFieldState(),
    )
}

@Preview
@Composable
internal fun SwapFormScreenPreview3() {

    SwapScreen(
        state = SwapFormUiModel(
            selectedSrcToken = tokenInput,
            selectedDstToken = longTokenInput,
            srcFiatValue = "5.25",
            estimatedDstTokenValue = "12.80",
            estimatedDstFiatValue = "5.24",
            provider = UiText.DynamicString("ThorSwap"),
            networkFee = "0.02 RUNE",
            networkFeeFiat = "0.004 USD",
            totalFee = "0.024 USD",
            fee = "0.02 RUNE",
            error = null,
            formError = null,
            isSwapDisabled = false,
            isLoading = false,
            isLoadingNextScreen = false,
            expiredAt = Clock.System.now()
        ),
        srcAmountTextFieldState = TextFieldState(),
    )
}

@Preview
@Composable
internal fun SwapFormScreenPreview4() {

    SwapScreen(
        state = SwapFormUiModel(
            selectedSrcToken = longTokenInput,
            selectedDstToken = longTokenInput,
            srcFiatValue = "5.25",
            estimatedDstTokenValue = "12.80",
            estimatedDstFiatValue = "5.24",
            provider = UiText.DynamicString("ThorSwap"),
            networkFee = "0.02 RUNE",
            networkFeeFiat = "0.004 USD",
            totalFee = "0.024 USD",
            fee = "0.02 RUNE",
            error = null,
            formError = null,
            isSwapDisabled = false,
            isLoading = false,
            isLoadingNextScreen = false,
            expiredAt = Clock.System.now(),
            referralBpsDiscount = 10,
            referralBpsDiscountFiatValue = "0.001 USD",
            vultBpsDiscount = 30,
            vultBpsDiscountFiatValue = "0.003 USD",
            tierType = TierType.GOLD,
        ),
        srcAmountTextFieldState = TextFieldState(),
    )
}

private val longTokenInput = TokenBalanceUiModel(
    model = SendSrc(
        address = Address(
            chain = Chain.ThorChain,
            address = "thor1xyzabc123",
            accounts = listOf(
                Account(
                    token = Coin(
                        chain = Chain.ThorChain,
                        ticker = "RUNE",
                        logo = "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
                        address = "thor1xyzabc123",
                        decimal = 8,
                        hexPublicKey = "0xabc123def456",
                        priceProviderID = "thorchain-rune",
                        contractAddress = "",
                        isNativeToken = true
                    ),
                    tokenValue = TokenValue(BigInteger("2500000000"), "RUNE", 8),
                    fiatValue = FiatValue(BigDecimal("5.25"), "USD"),
                    price = FiatValue(BigDecimal("2.10"), "USD")
                )
            )
        ),
        account = Account(
            token = Coin(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                logo = "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
                address = "thor1xyzabc123",
                decimal = 8,
                hexPublicKey = "0xabc123def456",
                priceProviderID = "thorchain-rune",
                contractAddress = "",
                isNativeToken = true
            ),
            tokenValue = TokenValue(BigInteger("2500000000"), "RUNE", 8),
            fiatValue = FiatValue(BigDecimal("5.25"), "USD"),
            price = FiatValue(BigDecimal("2.10"), "USD")
        )
    ),
    title = "LP-THOR.RUJI/ ETH.USDC-XYK",
    balance = "0.11412095",
    fiatValue = "5.25",
    isNativeToken = true,
    isLayer2 = false,
    tokenStandard = "THORCHAIN",
    tokenLogo = "https://assets.coingecko.com/coins/images/6595/large/RUNE.png",
    chainLogo = Chain.ThorChain.logo
)

private val tokenInput = TokenBalanceUiModel(
    model = SendSrc(
        address = Address(
            chain = Chain.TerraClassic,
            address = "maya1def456ghi789",
            accounts = listOf(
                Account(
                    token = Coin(
                        chain = Chain.TerraClassic,
                        ticker = "CACAO",
                        logo = "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
                        address = "maya1def456ghi789",
                        decimal = 6,
                        hexPublicKey = "0xdef789ghi012",
                        priceProviderID = "mayachain-cacao",
                        contractAddress = "",
                        isNativeToken = true
                    ),
                    tokenValue = TokenValue(BigInteger("1000000000"), "CACAO", 6),
                    fiatValue = FiatValue(BigDecimal("4.10"), "USD"),
                    price = FiatValue(BigDecimal("0.41"), "USD")
                )
            )
        ),
        account = Account(
            token = Coin(
                chain = Chain.MayaChain,
                ticker = "CACAO",
                logo = "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
                address = "maya1def456ghi789",
                decimal = 6,
                hexPublicKey = "0xdef789ghi012",
                priceProviderID = "mayachain-cacao",
                contractAddress = "",
                isNativeToken = true
            ),
            tokenValue = TokenValue(BigInteger("1000000000"), "CACAO", 6),
            fiatValue = FiatValue(BigDecimal("4.10"), "USD"),
            price = FiatValue(BigDecimal("0.41"), "USD")
        )
    ),
    title = "CACAO",
    balance = "10.0",
    fiatValue = "4.10",
    isNativeToken = true,
    isLayer2 = false,
    tokenStandard = "THORCHAIN",
    tokenLogo = "https://assets.coingecko.com/coins/images/40000/large/CACAO.png",
    chainLogo = Chain.ThorChain.logo
)