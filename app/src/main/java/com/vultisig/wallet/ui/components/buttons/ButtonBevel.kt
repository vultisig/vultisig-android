package com.vultisig.wallet.ui.components.buttons

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private val HighlightColor = Color.White
private val ShadeColor = Color(0xFF0F1C3E)

/**
 * The inset top highlight and bottom shade that give the styleguide buttons their bevelled look.
 * Both are inner shadows in the design, so they are drawn as such rather than approximated with a
 * gradient.
 */
internal class ButtonBevel(val highlight: Shadow, val shade: Shadow) {
    companion object {
        /** The CTA (primary) fill. */
        val Strong =
            ButtonBevel(
                highlight =
                    Shadow(
                        radius = 1.9.dp,
                        color = HighlightColor,
                        alpha = 0.24f,
                        offset = DpOffset(x = 0.dp, y = 1.dp),
                    ),
                shade =
                    Shadow(
                        radius = 1.6.dp,
                        color = ShadeColor,
                        alpha = 0.48f,
                        offset = DpOffset(x = 0.dp, y = (-1).dp),
                    ),
            )

        /** The secondary (surface) fill and every disabled button. */
        val Soft =
            ButtonBevel(
                highlight =
                    Shadow(
                        radius = 1.dp,
                        color = HighlightColor,
                        alpha = 0.1f,
                        offset = DpOffset(x = 0.dp, y = 1.dp),
                    ),
                shade =
                    Shadow(
                        radius = 0.5.dp,
                        color = ShadeColor,
                        offset = DpOffset(x = 0.dp, y = (-1).dp),
                    ),
            )
    }
}

internal fun Modifier.bevel(shape: Shape, bevel: ButtonBevel): Modifier =
    innerShadow(shape, bevel.highlight).innerShadow(shape, bevel.shade)
