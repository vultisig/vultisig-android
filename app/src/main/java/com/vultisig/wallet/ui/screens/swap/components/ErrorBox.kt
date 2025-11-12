package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
private fun TopShape(
    modifier: Modifier = Modifier,
    shapeSize: DpSize = DpSize(
        width = 35.dp,
        height = 15.dp
    ),
    shapeColor: Color,
) {
    Canvas(
        modifier = modifier
            .width(shapeSize.width)
            .height(shapeSize.height)
    ) {
        val path = Path().apply {
            val maxWidth = size.width
            val maxHeight = size.height + 1
            val offSet = 20f


            moveTo(0f, maxHeight)
            relativeLineTo(offSet, 0f)
            lineTo(
                maxWidth.div(2), 0f
            )
            lineTo(
                maxWidth - offSet, maxHeight
            )
            relativeLineTo(offSet, 0f)
            close()
        }

        val paint = Paint().apply {
            color = shapeColor
            style = PaintingStyle.Fill
            pathEffect = PathEffect.cornerPathEffect(20f)
        }

        drawIntoCanvas { canvas ->
            canvas.drawPath(path, paint)
        }
    }
}


@Composable
internal fun ErrorBox(
    modifier: Modifier = Modifier,
    errorTitle: String,
    errorMessage: String,
    offset: IntOffset,
    onDismissClick: () -> Unit,
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismissClick,
    ) {
        ErrorBoxPopupContent(
            modifier = modifier,
            errorTitle = errorTitle,
            errorMessage = errorMessage,
            onDismissClick = onDismissClick
        )
    }

}

@Composable
private fun ErrorBoxPopupContent(
    modifier: Modifier = Modifier,
    errorTitle: String,
    errorMessage: String,
    onDismissClick: () -> Unit,
) {

    val shapeColor = Theme.colors.neutrals.n200
    Column(
        modifier = modifier
            .clickable(onClick = onDismissClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopShape(
            shapeColor = shapeColor
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    color = shapeColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                Row {
                    Text(
                        text = errorTitle,
                        style = Theme.brockmann.body.m.medium,
                        color = Theme.colors.text.inverse
                    )
                    UiSpacer(
                        weight = 1f
                    )
                    UiIcon(
                        drawableResId = R.drawable.x,
                        size = 16.dp,
                        tint = Theme.colors.text.button.disabled,
                    )
                }

                UiSpacer(
                    size = 2.dp
                )

                Text(
                    text = errorMessage,
                    color = Theme.colors.text.extraLight,
                    style = Theme.brockmann.supplementary.footnote
                )
            }
        }
    }
}

@Preview
@Composable
private fun ErrorBoxPreview() {
    ErrorBoxPopupContent(
        modifier = Modifier.width(250.dp),
        errorTitle = "Insufficient funds",
        errorMessage = "Insufficient funds to execute the swap. Please fund the wallet.",
        onDismissClick = {}
    )
}