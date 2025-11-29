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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DottyBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Companion.Transparent,
        scrimColor = Color.Companion.Black.copy(alpha = 0.32f),
        shape = RectangleShape,
        content = {
            Box {
                Column(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(
                                topStart = 32.dp,
                                topEnd = 32.dp,
                            )
                        )
                        .background(Theme.v2.colors.backgrounds.primary)
                        .drawBehind {
                            generateBackgroundDots(
                                dotColor = Color(0xff172854),
                            )
                            bottomFade()
                        },
                    horizontalAlignment = Alignment.Companion.CenterHorizontally,
                    content = content,
                )
                DragHandler(
                    modifier = Modifier.Companion
                        .padding(top = 8.dp)
                        .align(Alignment.Companion.TopCenter)
                )
            }

        }
    )
}


private fun DrawScope.bottomFade() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                colors.backgrounds.secondary
            )
        ),
    )
}


private fun DrawScope.generateBackgroundDots(
    stepSize: Float = 50f,
    dotRadius: Float = 3f,
    dotColor: Color = colors.neutrals.n50
) {
    val width = size.width
    val height = size.height

    val dotsX = (width / stepSize).toInt() + 1
    val dotsY = (height / stepSize).toInt() + 1

    val offsetX = (width - (dotsX - 1) * stepSize) / 2
    val offsetY = (height - (dotsY - 1) * stepSize) / 2

    for (row in 0 until dotsY) {
        for (col in 0 until dotsX) {
            val x = offsetX + col * stepSize
            val y = offsetY + row * stepSize

            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
        }
    }
}