package com.vultisig.wallet.ui.screens.swap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsBasicTextField
import com.vultisig.wallet.ui.components.rememberKeyboardVisibilityAsState
import com.vultisig.wallet.ui.components.v2.fastselection.contentWithFastSelection
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.components.v2.utils.toPx
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.swap.components.AdvancedSwapSettings
import com.vultisig.wallet.ui.screens.swap.components.DstTokenInput
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.screens.swap.components.PercentagePicker
import com.vultisig.wallet.ui.screens.swap.components.QuoteTimer
import com.vultisig.wallet.ui.screens.swap.components.SrcTokenInput
import com.vultisig.wallet.ui.screens.swap.components.SwapFeeBreakdown
import com.vultisig.wallet.ui.screens.swap.components.SwapTokenFlipButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

internal fun NavGraphBuilder.swapScreen(navController: NavHostController) {
    contentWithFastSelection<Route.Swap.SwapMain, Route.Swap>(navController = navController) {
        onDragStart,
        onDrag,
        onDragEnd ->
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
            onValidateAmount = model::validateAmount,
            onSlippageSelected = model::setSlippageBps,
            onGasLimitSelected = model::setGasLimit,
            onExternalRecipientSelected = model::setExternalRecipient,
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
    onValidateAmount: () -> Unit = {},
    onSlippageSelected: (Int?) -> Unit = {},
    onGasLimitSelected: (Long?) -> Unit = {},
    onExternalRecipientSelected: (String?) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    // Market/Limit mode is local UI state: Limit-order execution is out of scope (#4858), so the
    // ViewModel has no stake in it yet. Market hosts the existing swap form; Limit is a
    // placeholder.
    var selectedMode by rememberSaveable { mutableStateOf(SwapMode.Market) }

    val interactionSource = remember { MutableInteractionSource() }
    val isSrcAmountFocused by interactionSource.collectIsFocusedAsState()

    var hasSrcAmountBeenFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isSrcAmountFocused) {
        if (isSrcAmountFocused) {
            hasSrcAmountBeenFocused = true
        } else if (hasSrcAmountBeenFocused) {
            onValidateAmount()
        }
    }

    val isShowingKeyboard by rememberKeyboardVisibilityAsState()

    V2Scaffold(
        title = stringResource(R.string.chain_account_view_swap),
        onBackClick = onBackClick,
        actions = {
            if (selectedMode == SwapMode.Market && state.quoteDisplay.expiredAt != null) {
                QuoteTimer(expiredAt = state.quoteDisplay.expiredAt)
            }
        },
        content = {
            var topCenter by remember { mutableStateOf(Offset.Zero) }
            var bottomCenter by remember { mutableStateOf(Offset.Zero) }
            val space = 8.dp

            var flipButtonBottomCenter by remember { mutableStateOf(Offset.Zero) }

            val error = state.error ?: state.formError

            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SwapModeTabs(
                        selectedMode = selectedMode,
                        onSelectMode = { selectedMode = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (selectedMode == SwapMode.Limit) {
                        Text(
                            text = stringResource(R.string.swap_limit_coming_soon),
                            style = Theme.brockmann.body.m.medium,
                            color = Theme.v2.colors.text.tertiary,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                    } else {
                        Box {
                            Column(verticalArrangement = Arrangement.spacedBy(space)) {
                                Box {
                                    SrcTokenInput(
                                        isLoading = state.isLoading,
                                        title = stringResource(R.string.swap_form_from_title),
                                        selectedToken = state.selectedSrcToken,
                                        fiatValue = state.srcFiatValue,
                                        space = space,
                                        onSelectNetworkClick = onSelectSrcNetworkClick,
                                        onSelectTokenClick = onSelectSrcToken,
                                        onCircleBoundsChanged = { topCenter = it },
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel,
                                        onDragStart = onDragStart,
                                        onLongPressStarted = onSrcLongPressStarted,
                                        textFieldContent = {
                                            VsBasicTextField(
                                                textFieldState = srcAmountTextFieldState,
                                                style = Theme.brockmann.headings.title2,
                                                color = Theme.v2.colors.text.secondary,
                                                textAlign = TextAlign.End,
                                                hint = "0",
                                                hintColor = Theme.v2.colors.text.tertiary,
                                                hintStyle = Theme.brockmann.headings.title2,
                                                lineLimits = TextFieldLineLimits.SingleLine,
                                                interactionSource = interactionSource,
                                                keyboardOptions =
                                                    KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Done,
                                                    ),
                                                modifier =
                                                    Modifier.fillMaxWidth()
                                                        .testTag("SwapFormScreen.fromAmount"),
                                            )
                                        },
                                    )
                                    SwapTokenFlipButton(
                                        isLoading = state.isLoading || state.isLoadingNextScreen,
                                        hasError = error != null,
                                        topCenter = topCenter,
                                        bottomCenter = bottomCenter,
                                        space = space,
                                        onFlip = onFlipSelectedTokens,
                                        onBoundsChanged = { flipButtonBottomCenter = it },
                                    )
                                }

                                // Keep the last (or indicative) destination value on screen while a
                                // new
                                // quote loads; only fall back to the skeleton when there is nothing
                                // to
                                // show yet — the first quote for a pair (#4712).
                                val dstHasValue =
                                    state.quoteDisplay.estimatedDstTokenValue.isNotBlank() &&
                                        state.quoteDisplay.estimatedDstTokenValue != "0"
                                DstTokenInput(
                                    title = stringResource(R.string.swap_form_dst_token_title),
                                    isLoading = state.isLoading && !dstHasValue,
                                    selectedToken = state.selectedDstToken,
                                    fiatValue = state.quoteDisplay.estimatedDstFiatValue,
                                    space = space,
                                    onSelectNetworkClick = onSelectDstNetworkClick,
                                    onSelectTokenClick = onSelectDstToken,
                                    onCircleBoundsChanged = { bottomCenter = it },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    onDragStart = onDragStart,
                                    onLongPressStarted = onDstLongPressStarted,
                                    textFieldContent = {
                                        Text(
                                            text = state.quoteDisplay.estimatedDstTokenValue,
                                            style = Theme.brockmann.headings.title2,
                                            // Grey the value while it is only an indicative
                                            // spot-price
                                            // estimate; firm quotes render in the brighter
                                            // secondary.
                                            color =
                                                if (state.quoteDisplay.isDstEstimated)
                                                    Theme.v2.colors.text.tertiary
                                                else Theme.v2.colors.text.secondary,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }

                        SwapFeeBreakdown(
                            isLoading = state.isLoading,
                            quoteDisplay = state.quoteDisplay,
                            feeBreakdown = state.feeBreakdown,
                            discountInfo = state.discountInfo,
                        )
                    }
                }

                if (selectedMode == SwapMode.Market) {
                    error?.let {
                        val errorBoxWidth = 200.dp
                        val errorWidthBoxPx = errorBoxWidth.toPx().toInt()
                        val spacePx = space.toPx().toInt()
                        HintBox(
                            modifier = Modifier.width(errorBoxWidth),
                            message = error.asString(),
                            onDismissClick = onDismissError,
                            title = stringResource(R.string.dialog_default_error_title),
                            offset =
                                IntOffset(
                                    x = flipButtonBottomCenter.x.toInt() - errorWidthBoxPx.div(2),
                                    y = flipButtonBottomCenter.y.toInt() + spacePx,
                                ),
                            isVisible = true,
                        )
                    }
                }
            }
        },
        bottomBar = {
            // No Swap CTA in Limit mode (placeholder until limit orders ship, #4858).
            if (selectedMode == SwapMode.Market) {
                AnimatedContent(
                    targetState = isSrcAmountFocused && isShowingKeyboard,
                    transitionSpec = {
                        val animationSpec = tween<IntOffset>(durationMillis = 60)
                        slideInVertically(animationSpec) { it } togetherWith
                            slideOutVertically(animationSpec) { it }
                    },
                ) { showPercentagePicker ->
                    if (showPercentagePicker) {
                        PercentagePicker(
                            enableMaxAmount = state.enableMaxAmount,
                            onSelectSrcPercentage = onSelectSrcPercentage,
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            AdvancedSwapSettings(
                                slippageBps = state.slippageBps,
                                onSlippageSelected = onSlippageSelected,
                                gasLimitOverride = state.gasLimitOverride,
                                isGasLimitApplicable = state.isGasLimitApplicable,
                                onGasLimitSelected = onGasLimitSelected,
                                externalRecipient = state.externalRecipient,
                                onExternalRecipientSelected = onExternalRecipientSelected,
                            )
                            VsButton(
                                label =
                                    if (srcAmountTextFieldState.text.isEmpty()) {
                                        stringResource(R.string.swap_swap_button_fill_in_amount)
                                    } else {
                                        stringResource(R.string.swap_swap_button)
                                    },
                                variant = VsButtonVariant.Primary,
                                state =
                                    if (state.isSwapDisabled || state.isLoading) {
                                        VsButtonState.Disabled
                                    } else {
                                        VsButtonState.Enabled
                                    },
                                onClick = {
                                    focusManager.clearFocus(true)
                                    onSwap()
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .testTag("SwapFormScreen.swapButton"),
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * Swap mode tabs (#4858). Only [Market] is functional; [Limit] is a visible placeholder until
 * limit-order execution ships. `NFTs – Soon` from the design stays hidden.
 */
internal enum class SwapMode {
    Market,
    Limit,
}

@Composable
private fun SwapModeTabs(
    selectedMode: SwapMode,
    onSelectMode: (SwapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        VsTabGroup(index = selectedMode.ordinal) {
            tab {
                VsTab(
                    label = stringResource(R.string.swap_mode_market),
                    onClick = { onSelectMode(SwapMode.Market) },
                )
            }
            tab {
                VsTab(
                    label = stringResource(R.string.swap_mode_limit),
                    onClick = { onSelectMode(SwapMode.Limit) },
                )
            }
        }
    }
}
