package com.vultisig.wallet.ui.components.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun UiCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    indicatorBrush: Brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to Theme.v2.colors.primary.accent2,
            1.0f to Theme.v2.colors.buttons.primary,
        ),
    ),
) {
    val coercedProgress = { progress().coerceIn(0f, 1f) }

    val trackColor = Theme.v2.colors.backgrounds.tertiary_2

    val stroke = with(LocalDensity.current) {
        Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val targetScale = 0.30f
    val progressToStartScaling = 0.75f

    val doneOpacity = coercedProgress().takeIf { it >= progressToStartScaling } ?: 0f

    val circlesScale = if (coercedProgress() > progressToStartScaling) lerp(
        1.0f,
        stop = targetScale,
        (1.0f - targetScale).div(1.0f - progressToStartScaling)
            .times(coercedProgress() - progressToStartScaling)
    ) else 1.0f


    val doneCheckBounceAngel = if ((coercedProgress() >= progressToStartScaling))
        (140 / (1 - progressToStartScaling)).times(coercedProgress() - progressToStartScaling)
    else 0.0f

    val tickScale = if (coercedProgress() > progressToStartScaling)
        (coercedProgress() - progressToStartScaling).div(1 - progressToStartScaling).times(
            //simulate bounce effect
            sin(doneCheckBounceAngel.times(PI).div(180))
        ).toFloat()
    else 0f

    val tickCircleColor = Theme.v2.colors.buttons.primary.copy(
        alpha = if (coercedProgress() > progressToStartScaling)
            (coercedProgress() - progressToStartScaling).div(1.0f - progressToStartScaling)
        else 0.5f
    )

    val tickVector = ImageVector.vectorResource(id = R.drawable.done_check)
    val tickPainter = rememberVectorPainter(image = tickVector)
    val doneTextMeasurer = rememberTextMeasurer()
    val doneText = stringResource(R.string.circular_progress_indicator_done)
    val doneTextStyle =
        Theme.menlo.heading5.copy(color = Theme.v2.colors.neutrals.n50.copy(alpha = doneOpacity))
    val fullCircle = 360f

    Canvas(
        modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress(), 0f..1f)
            }
            .size(48.dp)
    ) {
        val startAngle = 270f
        val sweep = coercedProgress() * fullCircle
        val diameterOffset = stroke.width / 2
        val arcDimen = size.width - 2 * diameterOffset

        val doneCheckCircleScale =
            1 - circlesScale + if (coercedProgress() > progressToStartScaling)
                diameterOffset.times(2).div(arcDimen) else 0f
        val spaceBetweenDoneCircleAndText = 50


        scale(circlesScale) {
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = fullCircle,
                useCenter = false,
                topLeft = Offset(diameterOffset, diameterOffset),
                size = Size(arcDimen, arcDimen),
                style = stroke,
            )
        }


        scale(circlesScale) {
            drawArc(
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(diameterOffset, diameterOffset),
                size = Size(arcDimen, arcDimen),
                style = stroke,
                brush = indicatorBrush,
            )
        }


        scale(doneCheckCircleScale) {
            drawArc(
                startAngle = startAngle,
                sweepAngle = fullCircle,
                useCenter = false,
                topLeft = Offset(diameterOffset, diameterOffset),
                size = Size(arcDimen, arcDimen),
                style = Fill,
                brush = SolidColor(tickCircleColor),
            )
        }


        scale(tickScale) {
            val tickSize = 250f
            translate(
                left = arcDimen.div(2) - tickSize.div(2) + diameterOffset,
                top = arcDimen.div(2) - tickSize.div(2) + diameterOffset
            ) {
                with(tickPainter) {
                    draw(Size(tickSize, tickSize), alpha = coercedProgress())
                }
            }
        }

        drawText(
            textMeasurer = doneTextMeasurer,
            text = doneText,
            style = doneTextStyle,
            topLeft = Offset(
                x = center.x - doneTextMeasurer.measure(doneText, doneTextStyle).size.width.div(2),
                y = center.y - doneTextMeasurer.measure(
                    doneText,
                    doneTextStyle
                ).size.height.div(2) + arcDimen.times(targetScale).times(coercedProgress())
                    .plus(spaceBetweenDoneCircleAndText),
            )
        )
    }
}

@Preview
@Composable
private fun CircularProgressIndicatorPreview() {
    UiCircularProgressIndicator(progress = { 0.5f })
}