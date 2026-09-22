package com.vultisig.wallet.ui.components.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Mini
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Small
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Default
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Disabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.*
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

enum class VsButtonVariant {
    Primary,
    Secondary,
    Error,
    Tertiary,
}

enum class VsButtonState {
    Enabled,
    Disabled,
    Default,
}

enum class VsButtonSize {
    Medium,
    Small,
    Mini,
}

/**
 * The inset top highlight and bottom shade that give the styleguide button its bevelled look. Both
 * are inner shadows in the design, so they are drawn as such rather than approximated with a
 * gradient.
 */
private class Bevel(val highlight: Shadow, val shade: Shadow)

private val BevelHighlightColor = Color.White
private val BevelShadeColor = Color(0xFF0F1C3E)

private val PrimaryBevel =
    Bevel(
        highlight =
            Shadow(
                radius = 1.9.dp,
                color = BevelHighlightColor,
                alpha = 0.24f,
                offset = DpOffset(x = 0.dp, y = 1.dp),
            ),
        shade =
            Shadow(
                radius = 1.6.dp,
                color = BevelShadeColor,
                alpha = 0.48f,
                offset = DpOffset(x = 0.dp, y = (-1).dp),
            ),
    )

private val SoftBevel =
    Bevel(
        highlight =
            Shadow(
                radius = 1.dp,
                color = BevelHighlightColor,
                alpha = 0.1f,
                offset = DpOffset(x = 0.dp, y = 1.dp),
            ),
        shade =
            Shadow(
                radius = 0.5.dp,
                color = BevelShadeColor,
                offset = DpOffset(x = 0.dp, y = (-1).dp),
            ),
    )

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    variant: VsButtonVariant = Primary,
    state: VsButtonState = Enabled,
    size: VsButtonSize = Medium,
    shape: Shape? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val backgroundColor by
        animateColorAsState(
            when (state) {
                Enabled,
                Default ->
                    when (variant) {
                        Primary -> colors.buttons.ctaPrimary
                        Secondary -> colors.backgrounds.tertiary_2
                        Error -> colors.alerts.error
                        Tertiary -> colors.neutrals.n50
                    }

                Disabled ->
                    when (variant) {
                        Primary,
                        Secondary -> colors.buttons.disabled
                        Error -> colors.buttons.disabledError
                        Tertiary -> colors.neutrals.n400
                    }
            },
            label = "VsButton.backgroundColor",
        )

    val borderColor by
        animateColorAsState(
            if (variant == Secondary && state != Disabled) colors.variables.bordersExtraLight
            else Color.Transparent,
            label = "VsButton.borderColor",
        )

    val bevel =
        when (variant) {
            Primary -> if (state == Disabled) SoftBevel else PrimaryBevel
            Secondary -> SoftBevel
            Error,
            Tertiary -> null
        }

    val resolvedShape = shape ?: Theme.v2.radius.pill

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .background(color = backgroundColor, shape = resolvedShape)
                .border(width = 1.dp, color = borderColor, shape = resolvedShape)
                .then(
                    if (bevel != null) {
                        Modifier.innerShadow(resolvedShape, bevel.highlight)
                            .innerShadow(resolvedShape, bevel.shade)
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = state != Disabled && !isLoading, onClick = onClick)
                .then(
                    when (size) {
                        Medium -> Modifier.padding(vertical = 14.dp, horizontal = 24.dp)

                        Small -> Modifier.padding(vertical = 12.dp, horizontal = 24.dp)

                        Mini -> Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    }
                ),
    ) {
        // Keep the content composed and measured while loading so the button retains its
        // natural size; hide it visually and overlay the loading indicator on top.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = if (isLoading) Modifier.alpha(0f) else Modifier,
        ) {
            content()
        }

        if (isLoading) {
            VsButtonLoadingIndicator(size = size)
        }
    }
}

/**
 * Looping Lottie loading indicator rendered inside a [VsButton] while an async action is in flight.
 *
 * @param size the button size, used to scale the indicator so the button keeps its normal height.
 */
@Composable
private fun VsButtonLoadingIndicator(size: VsButtonSize) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.button_loading))

    val progress by
        animateLottieCompositionAsState(
            composition = composition,
            iterations = LottieConstants.IterateForever,
        )

    val indicatorSize =
        when (size) {
            Medium -> 24.dp
            Small,
            Mini -> 20.dp
        }

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(indicatorSize),
    )
}

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    label: String? = null,
    iconLeft: Int? = null,
    iconRight: Int? = null,
    variant: VsButtonVariant = Primary,
    state: VsButtonState = Enabled,
    size: VsButtonSize = Medium,
    shape: Shape? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    VsButton(
        modifier = modifier,
        variant = variant,
        state = state,
        size = size,
        shape = shape,
        isLoading = isLoading,
        onClick = onClick,
    ) {
        val contentColor by
            animateColorAsState(
                when (state) {
                    Enabled,
                    Default ->
                        if (variant == Tertiary) colors.text.inverse else colors.text.button.primary

                    Disabled -> colors.text.button.disabled
                },
                label = "VsButton.contentColor",
            )

        val iconSize =
            when (size) {
                Medium -> 20.dp
                Small,
                Mini -> 16.dp
            }

        if (iconLeft != null) {
            UiIcon(drawableResId = iconLeft, size = iconSize, tint = contentColor)
        }

        if (label != null) {
            AutoSizingText(
                text = label,
                style = Theme.brockmann.button.semibold.medium,
                color = contentColor,
            )
        }

        if (iconRight != null) {
            UiIcon(drawableResId = iconRight, size = iconSize, tint = contentColor)
        }
    }
}

@Preview
@Composable
private fun VsButtonPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VsButton(
            label = "Primary",
            variant = Primary,
            state = Enabled,
            size = Medium,
            iconLeft = R.drawable.ic_caret_left,
            iconRight = R.drawable.ic_caret_right,
            onClick = {},
        )
        VsButton(
            label = "Primary Default",
            variant = Primary,
            state = Default,
            size = Medium,
            onClick = {},
        )

        VsButton(
            label = "Secondary Default",
            variant = Secondary,
            state = Default,
            size = Medium,
            onClick = {},
        )

        VsButton(
            label = "Primary Disabled",
            variant = Primary,
            state = Disabled,
            size = Medium,
            onClick = {},
        )

        VsButton(
            label = "Secondary",
            variant = Secondary,
            state = Enabled,
            size = Medium,
            onClick = {},
        )

        VsButton(
            label = "Secondary Disabled",
            variant = Secondary,
            state = Disabled,
            size = Medium,
            onClick = {},
        )

        VsButton(label = "Error", variant = Error, size = Medium, onClick = {})

        VsButton(
            label = "Primary Enabled Small",
            variant = Primary,
            state = Enabled,
            size = Small,
            onClick = {},
        )

        VsButton(
            label = "Primary Mini Small",
            variant = Primary,
            state = Enabled,
            size = Mini,
            onClick = {},
        )

        VsButton(label = "Tertiary Mini Small", variant = Tertiary, state = Enabled, onClick = {})

        VsButton(label = "Tertiary Mini Small", variant = Tertiary, state = Disabled, onClick = {})

        VsButton(label = "Tertiary Mini Small", variant = Tertiary, state = Default, onClick = {})

        VsButton(
            label = "Primary Loading",
            variant = Primary,
            state = Enabled,
            isLoading = true,
            onClick = {},
        )
    }
}
