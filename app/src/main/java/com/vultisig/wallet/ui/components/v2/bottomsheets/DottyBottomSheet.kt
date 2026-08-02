package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DottyBottomSheet(
    onExpand: () -> Unit = {},
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {

    // Nothing drives expand() here on purpose: ModalBottomSheet already animates to its first
    // resting anchor on first composition. Expanding on the same frame short-circuits that
    // animation and the sheet lands at its target without sliding.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Hidden) {
            onExpand()
        }
    }

    // ModalBottomSheet runs its own show() before the first layout, while the sheet still has no
    // anchors and therefore no partial one to aim at, so it always targets Expanded and the anchor
    // handler then keeps that target. Retarget the moment the anchors exist: the slide-in has
    // barely begun by then, so the sheet still reads as travelling to its rest position once.
    LaunchedEffect(sheetState, skipPartiallyExpanded) {
        if (skipPartiallyExpanded) return@LaunchedEffect
        snapshotFlow { sheetState.hasPartiallyExpandedState }.first { it }
        if (sheetState.targetValue == SheetValue.Expanded) {
            sheetState.partialExpand()
        }
    }

    // Distance from the content's top to the bottom of the window. At the partial rest position
    // ModalBottomSheet translates the whole sheet down, so the content runs past the bottom of the
    // screen and is cut by the window edge rather than by its own bounds — this is where that cut
    // lands, tracked through the drag so the fade stays on it.
    var cutEdgeFromContentTop by remember { mutableFloatStateOf(Float.NaN) }
    val windowInfo = LocalWindowInfo.current
    val cutEdgeFadeColor = colors.backgrounds.secondary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = RectangleShape,
        content = {
            Box {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            .background(Theme.v2.colors.backgrounds.primary)
                            .then(
                                if (skipPartiallyExpanded) Modifier
                                else
                                    Modifier.onGloballyPositioned { coordinates ->
                                        cutEdgeFromContentTop =
                                            windowInfo.containerSize.height -
                                                coordinates.positionInWindow().y
                                    }
                            )
                            .drawWithCache {
                                val dots =
                                    rememberDotsPath(
                                        stepSize = 72f,
                                        dotRadius = 2.5f,
                                        dotColor = Color(0xff172854),
                                    )
                                val fadeBrush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(Color.Transparent, colors.backgrounds.secondary)
                                    )
                                onDrawWithContent {
                                    drawPath(dots.path, color = dots.color)
                                    drawRect(brush = fadeBrush)
                                    drawContent()
                                    drawCutEdgeFade(
                                        cutEdgeY = cutEdgeFromContentTop,
                                        color = cutEdgeFadeColor,
                                    )
                                }
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
                DragHandler(
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter),
                    color = Theme.v2.colors.vibrant.primary,
                )
            }
        },
    )
}

/**
 * Fades the content into the sheet background at [cutEdgeY], so a sheet resting below full height
 * reads as "there is more below" rather than as a clipped view. Draws nothing once the content ends
 * above the cut — at the fully expanded anchor there is nothing being cut off to hint at.
 */
private fun ContentDrawScope.drawCutEdgeFade(cutEdgeY: Float, color: Color) {
    if (cutEdgeY.isNaN() || cutEdgeY >= size.height) return

    val fadeTop = (cutEdgeY - CUT_EDGE_FADE_HEIGHT.toPx()).coerceAtLeast(0f)
    val fadeHeight = cutEdgeY - fadeTop
    if (fadeHeight <= 0f) return

    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = fadeTop,
                endY = cutEdgeY,
            ),
        topLeft = Offset(x = 0f, y = fadeTop),
        size = Size(width = size.width, height = fadeHeight),
    )
}

private val CUT_EDGE_FADE_HEIGHT = 96.dp

private data class DotsPath(val path: Path, val color: Color)

private fun CacheDrawScope.rememberDotsPath(
    stepSize: Float = 72f,
    dotRadius: Float = 2.5f,
    dotColor: Color = colors.neutrals.n50,
): DotsPath {
    val width = size.width
    val height = size.height

    val dotsX = (width / stepSize).toInt() + 1
    val dotsY = (height / stepSize).toInt() + 1

    val offsetX = (width - (dotsX - 1) * stepSize) / 2
    val offsetY = (height - (dotsY - 1) * stepSize) / 2

    val path = Path()
    for (row in 0 until dotsY) {
        for (col in 0 until dotsX) {
            val x = offsetX + col * stepSize
            val y = offsetY + row * stepSize
            path.addOval(
                Rect(
                    left = x - dotRadius,
                    top = y - dotRadius,
                    right = x + dotRadius,
                    bottom = y + dotRadius,
                )
            )
        }
    }
    return DotsPath(path = path, color = dotColor)
}
