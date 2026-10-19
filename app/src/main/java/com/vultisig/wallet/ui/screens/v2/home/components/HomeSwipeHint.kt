package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val HomeSwipeHintTestTag = "home_swipe_hint"

/** Mirrors iOS `HomeSwipeHintView`. */
@Composable
internal fun HomeSwipeHint(
    anchorTopInWindow: Float,
    onDismiss: () -> Unit,
    onSwipe: (towardsNextTab: Boolean) -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    var overlayTopInWindow by remember { mutableFloatStateOf(0f) }
    val title = stringResource(R.string.home_swipe_hint_title)

    Box(
        modifier =
            Modifier.fillMaxSize()
                .testTag(HomeSwipeHintTestTag)
                .semantics { paneTitle = title }
                .onGloballyPositioned { overlayTopInWindow = it.positionInWindow().y }
                .background(Color.Black.copy(alpha = DIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } }
                .pointerInput(Unit) {
                    var dragX = 0f
                    val minimumDistance = SwipeMinimumDistance.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onDragEnd = {
                            if (abs(dragX) > minimumDistance) currentOnSwipe(dragX < 0)
                            else currentOnDismiss()
                        },
                    ) { change, amount ->
                        change.consume()
                        dragX += amount
                    }
                }
    ) {
        Layout(content = { Bubble(onDismiss = onDismiss) }) { measurables, constraints ->
            val bubble = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val anchorTop = anchorTopInWindow - overlayTopInWindow
                val y = anchorTop - ArrowGap.toPx() - bubble.height
                bubble.place(BubbleLeading.roundToPx(), y.roundToInt().coerceAtLeast(0))
            }
        }
    }
}

@Composable
private fun Bubble(onDismiss: () -> Unit) {
    Row(
        modifier =
            Modifier.width(BubbleWidth)
                .background(Theme.v2.colors.text.primary, TooltipShape)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + ArrowHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .background(
                        Theme.v2.colors.primary.accent4.copy(alpha = ICON_BACKGROUND_ALPHA),
                        Theme.v2.radius.sm,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(
                drawableResId = R.drawable.arrows_left_right,
                size = 16.dp,
                tint = Theme.v2.colors.primary.accent4,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_swipe_hint_title),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.inverse,
            )
            Text(
                text = stringResource(R.string.home_swipe_hint_description),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = stringResource(R.string.home_swipe_hint_got_it),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.primary.accent4,
                    modifier = Modifier.align(Alignment.CenterEnd).clickOnce(onClick = onDismiss),
                )
            }
        }
    }
}

/** A rounded rectangle with a downward arrow centred on its bottom edge. */
private val TooltipShape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val arrowHeight = with(density) { ArrowHeight.toPx() }
            val arrowHalfWidth = with(density) { ArrowHalfWidth.toPx() }
            val radius = with(density) { BubbleCornerRadius.toPx() }
            val bodyBottom = size.height - arrowHeight
            val centerX = size.width / 2
            val path =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = bodyBottom,
                            cornerRadius = CornerRadius(radius),
                        )
                    )
                    moveTo(centerX - arrowHalfWidth, bodyBottom)
                    lineTo(centerX, size.height)
                    lineTo(centerX + arrowHalfWidth, bodyBottom)
                    close()
                }
            return Outline.Generic(path)
        }
    }

private val BubbleWidth = 260.dp
private val BubbleLeading = 28.dp
private val ArrowGap = 4.dp
private val ArrowHeight = 10.dp
private val ArrowHalfWidth = 9.dp
private val BubbleCornerRadius = 12.dp
private val SwipeMinimumDistance = 30.dp
private const val DIM_ALPHA = 0.5f
private const val ICON_BACKGROUND_ALPHA = 0.12f
