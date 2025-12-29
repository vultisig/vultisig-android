package com.vultisig.wallet.ui.components.bottomsheet

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.bottomsheet.DragHandler
import com.vultisig.wallet.ui.theme.Theme.colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DottyBottomSheet(
    onExpand: () -> Unit = {},
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Hidden) {
            onExpand()
        }
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(
                                topStart = 32.dp,
                                topEnd = 32.dp,
                            )
                        )
                        .background(colors.backgrounds.primary)
                        .drawWithCache {
                            val dots = rememberDotsPath(
                                stepSize = 72f,
                                dotRadius = 2.5f,
                                dotColor = Color(0xff172854)
                            )
                            val fadeBrush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.backgrounds.secondary
                                )
                            )
                            onDrawBehind {
                                drawPath(dots.path, color = dots.color)
                                drawRect(brush = fadeBrush)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
                )
            }

        }
    )
}


private data class DotsPath(
    val path: Path,
    val color: Color,
)

private fun CacheDrawScope.rememberDotsPath(
    stepSize: Float = 72f,
    dotRadius: Float = 2.5f,
    dotColor: Color = colors.neutrals.n50
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