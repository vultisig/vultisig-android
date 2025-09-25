package com.vultisig.wallet.ui.screens.v2.chaintokens.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.bottomsheets.DragHandler
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.QrContainer
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.theme.Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TokenAddressQrBottomSheet(
    chainName: String,
    chainAddress: String,
    qrBitmapPainter: BitmapPainter?,
    onDismiss: () -> Unit = {},
    onShareQrClick: () -> Unit = {},
    onCopyAddressClick: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.8f),
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
                        .background(Theme.colors.buttons.secondary)
                        .drawBehind {
                            generateBackgroundDots(
                                dotColor = Color(0xff172854),
                            )
                            bottomFade()
                        }
                        .padding(
                            all = 30.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,

                    ) {


                    QrContainer(
                        chainName = chainName,
                        qrCode = qrBitmapPainter
                    )


                    UiSpacer(
                        size = 24.dp
                    )

                    Text(
                        text = chainAddress,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.colors.text.primary,
                        modifier = Modifier.width(232.dp),
                        textAlign = TextAlign.Center
                    )

                    UiSpacer(
                        size = 32.dp
                    )

                    Row {
                        VsButton(
                            variant = VsButtonVariant.Tertiary,
                            size = VsButtonSize.Small,
                            label = "Share",
                            onClick = onShareQrClick,
                            modifier = Modifier
                                .weight(1f)
                        )
                        UiSpacer(
                            size = 8.dp
                        )
                        VsButton(
                            variant = VsButtonVariant.Primary,
                            size = VsButtonSize.Small,
                            label = "Copy Address",
                            onClick = onCopyAddressClick,
                            modifier = Modifier
                                .weight(1f)
                        )

                    }


                    UiSpacer(
                        size = 12.dp
                    )
                }
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
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
                Colors.Default.backgrounds.secondary
            )
        ),
    )
}


private fun DrawScope.generateBackgroundDots(
    stepSize: Float = 50f,
    dotRadius: Float = 3f,
    dotColor: Color = Colors.Default.neutrals.n50
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