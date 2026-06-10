package com.vultisig.wallet.ui.screens.swap.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenAndChainLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.theme.Theme

/** Standard skeleton row shown in place of a form value while a swap quote loads. */
@Composable
internal fun loadingPlaceholder() {
    UiPlaceholderLoader(Modifier.height(16.dp).width(80.dp))
}

/**
 * Source-token (top) input card with a bottom cutout that hosts the flip button.
 *
 * @param space spacing used to offset the cutout so it lines up with the flip button.
 * @param onCircleBoundsChanged reports the cutout circle bounds, used to position the flip button.
 */
@Composable
internal fun SrcTokenInput(
    isLoading: Boolean,
    title: String,
    selectedToken: TokenBalanceUiModel?,
    fiatValue: String,
    space: Dp,
    onSelectNetworkClick: () -> Unit,
    onSelectTokenClick: () -> Unit,
    onCircleBoundsChanged: (Offset) -> Unit,
    @SuppressLint("ComposableLambdaParameterNaming") onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
    textFieldContent: @Composable ColumnScope.() -> Unit,
) {
    TokenInput(
        isLoading = isLoading,
        title = title,
        selectedToken = selectedToken,
        fiatValue = fiatValue,
        onSelectNetworkClick = onSelectNetworkClick,
        onSelectTokenClick = onSelectTokenClick,
        chainTestTag = "SwapFormScreen.fromChain",
        tokenTestTag = "SwapFormScreen.fromToken",
        shape =
            RoundedWithCutoutShape(
                cutoutPosition = CutoutPosition.Bottom,
                cutoutOffsetY = -space / 2,
                cutoutRadius = 28.dp,
                onCircleBoundsChanged = onCircleBoundsChanged,
            ),
        focused = true,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDragStart = onDragStart,
        onLongPressStarted = onLongPressStarted,
        textFieldContent = textFieldContent,
    )
}

/**
 * Destination-token (bottom) input card with a top cutout that hosts the flip button.
 *
 * @param space spacing used to offset the cutout so it lines up with the flip button.
 * @param onCircleBoundsChanged reports the cutout circle bounds, used to position the flip button.
 */
@Composable
internal fun DstTokenInput(
    isLoading: Boolean,
    title: String,
    selectedToken: TokenBalanceUiModel?,
    fiatValue: String,
    space: Dp,
    onSelectNetworkClick: () -> Unit,
    onSelectTokenClick: () -> Unit,
    onCircleBoundsChanged: (Offset) -> Unit,
    @SuppressLint("ComposableLambdaParameterNaming") onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
    textFieldContent: @Composable ColumnScope.() -> Unit,
) {
    TokenInput(
        title = title,
        isLoading = isLoading,
        selectedToken = selectedToken,
        fiatValue = fiatValue,
        onSelectNetworkClick = onSelectNetworkClick,
        onSelectTokenClick = onSelectTokenClick,
        chainTestTag = "SwapFormScreen.toChain",
        tokenTestTag = "SwapFormScreen.toToken",
        shape =
            RoundedWithCutoutShape(
                cutoutPosition = CutoutPosition.Top,
                cutoutOffsetY = -space / 2,
                cutoutRadius = 28.dp,
                onCircleBoundsChanged = onCircleBoundsChanged,
            ),
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDragStart = onDragStart,
        onLongPressStarted = onLongPressStarted,
        focused = false,
        textFieldContent = textFieldContent,
    )
}

@Composable
internal fun TokenInput(
    isLoading: Boolean,
    title: String,
    selectedToken: TokenBalanceUiModel?,
    fiatValue: String,
    onSelectNetworkClick: () -> Unit,
    onSelectTokenClick: () -> Unit,
    shape: Shape,
    focused: Boolean,
    modifier: Modifier = Modifier,
    chainTestTag: String? = null,
    tokenTestTag: String? = null,
    @SuppressLint("ComposableLambdaParameterNaming") onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
    textFieldContent: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .background(
                    color =
                        if (focused) Theme.v2.colors.backgrounds.secondary
                        else Theme.v2.colors.backgrounds.disabled,
                    shape = shape,
                )
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .clip(shape)
                .padding(all = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val selectedChain = selectedToken?.model?.address?.chain

            if (selectedChain != null) {
                Box(
                    modifier =
                        if (chainTestTag != null) Modifier.testTag(chainTestTag) else Modifier
                ) {
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
            }

            Text(
                text = selectedToken?.let { "${it.balance} ${it.title}" } ?: "",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.End,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = if (tokenTestTag != null) Modifier.testTag(tokenTestTag) else Modifier) {
                TokenChip(selectedToken = selectedToken, onSelectTokenClick = onSelectTokenClick)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isLoading && title == stringResource(R.string.swap_form_dst_token_title)) {
                    UiPlaceholderLoader(modifier = Modifier.height(24.dp).width(150.dp))
                } else {
                    textFieldContent()
                }

                if (isLoading) {
                    UiPlaceholderLoader(modifier = Modifier.height(16.dp).width(80.dp))
                } else {
                    Text(
                        text = fiatValue,
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
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
        modifier =
            Modifier.clickable(onClick = onSelectTokenClick)
                .onGloballyPositioned { coordinates ->
                    fieldPosition = coordinates.positionInWindow()
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val screenPosition =
                                Offset(
                                    x = fieldPosition.x + offset.x,
                                    y = fieldPosition.y + offset.y,
                                )
                            onDragStart(screenPosition)
                            onLongPressStarted(screenPosition)
                        },
                        onDrag = { change: PointerInputChange, _ ->
                            val localPos = change.position
                            val screenPos =
                                Offset(
                                    x = fieldPosition.x + localPos.x,
                                    y = fieldPosition.y + localPos.y,
                                )
                            onDrag(screenPos)
                            change.consume()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                }
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(99.dp),
                )
                .padding(all = 6.dp),
    ) {
        TokenAndChainLogo(
            tokenLogo = selectedToken?.tokenLogo ?: "",
            tokenTicker = selectedToken?.title ?: "",
            chainLogo =
                selectedToken?.chainLogo.takeIf {
                    selectedToken?.isNativeToken == false || selectedToken?.isLayer2 == true
                },
            chainLogoSize = 16.dp,
            tokenLogoSize = 36.dp,
        )

        UiSpacer(8.dp)

        Column {
            Text(
                text = selectedToken?.title ?: "",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )

            if (selectedToken?.isNativeToken == true) {
                Text(
                    text = stringResource(R.string.swap_form_native),
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }

        UiSpacer(4.dp)

        UiIcon(
            drawableResId = R.drawable.ic_chevron_right_small,
            size = 20.dp,
            tint = Theme.v2.colors.text.primary,
        )

        UiSpacer(6.dp)
    }
}
