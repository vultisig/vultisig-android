package com.vultisig.wallet.ui.components.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Mini
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Small
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Disabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Default
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.*
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

enum class VsButtonVariant {
    Primary, Secondary, Error, CTA, Tertiary,
}

enum class VsButtonState {
    Enabled, Disabled, Default
}

enum class VsButtonSize {
    Medium, Small, Mini
}

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    variant: VsButtonVariant = Primary,
    state: VsButtonState = Enabled,
    size: VsButtonSize = Medium,
    forceClickable: Boolean = false, // TODO: Review with designer, we should stick to current pattern
    shape: Shape? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        when (state) {
            Enabled -> when (variant) {
                Primary -> colors.buttons.tertiary
                Secondary -> colors.backgrounds.tertiary_2
                Error -> colors.alerts.error
                CTA -> colors.buttons.ctaPrimary
                Tertiary -> colors.neutrals.n50
            }

            Disabled -> when (variant) {
                Primary -> colors.buttons.disabled
                Secondary -> colors.backgrounds.tertiary_2
                Error -> colors.buttons.disabledError
                CTA -> colors.buttons.ctaDisabled
                Tertiary -> colors.neutrals.n400
            }

            Default -> when (variant) {
                Primary -> colors.buttons.tertiary
                Secondary -> colors.backgrounds.tertiary
                Error -> colors.alerts.error
                CTA -> colors.buttons.ctaPrimary
                Tertiary -> colors.neutrals.n50
            }
        },
        label = "VsButton.backgroundColor"
    )


    val borderColor by animateColorAsState(
        when (state) {
            Enabled -> when (variant) {
                Primary -> colors.buttons.tertiary
                Secondary -> colors.backgrounds.tertiary_2
                Error -> colors.alerts.error
                CTA -> colors.buttons.ctaPrimary
                Tertiary -> colors.neutrals.n50
            }

            Disabled -> when (variant) {
                Primary -> colors.buttons.disabled
                Secondary -> colors.backgrounds.tertiary_2
                Error -> colors.buttons.disabledError
                CTA -> colors.buttons.ctaDisabled
                Tertiary -> colors.neutrals.n400
            }

            Default -> when (variant) {
                Primary -> colors.buttons.tertiary
                Secondary -> colors.backgrounds.tertiary
                Error -> colors.alerts.error
                CTA -> colors.buttons.ctaPrimary
                Tertiary -> colors.neutrals.n50
            }
        },
        label = "VsButton.borderColor"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            Alignment.CenterHorizontally
        ),
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = shape ?: RoundedCornerShape(percent = 100)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape ?: RoundedCornerShape(percent = 100),
            )
            .clickable(
                enabled = state != Disabled || forceClickable,
                onClick = onClick
            )
            .then(
                when (size) {
                    Medium -> Modifier.padding(
                        vertical = 14.dp,
                        horizontal = 24.dp
                    )

                    Small -> Modifier.padding(
                        vertical = 12.dp,
                        horizontal = 24.dp
                    )

                    Mini -> Modifier.padding(
                        vertical = 8.dp,
                        horizontal = 12.dp
                    )
                }
            )
    ) {
        content()
    }
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
    forceClickable: Boolean = false,
    shape: Shape? = null,
    onClick: () -> Unit,
) {
    VsButton(
        modifier = modifier,
        variant = variant,
        state = state,
        size = size,
        shape = shape,
        forceClickable = forceClickable,
        onClick = onClick,
    ) {
        val contentColor by animateColorAsState(
            when {
                variant != Tertiary -> {
                    when (state) {
                        Enabled -> colors.text.button.primary
                        Disabled -> colors.text.button.disabled
                        Default -> colors.text.button.primary
                    }
                }
                else -> {
                    when (state) {
                        Enabled -> colors.text.inverse
                        Disabled -> colors.text.button.disabled
                        Default -> colors.text.inverse
                    }
                }
            },
            label = "VsButton.contentColor"
        )

        val iconSize = when (size) {
            Medium -> 20.dp
            Small, Mini -> 16.dp
        }

        if (iconLeft != null) {
            UiIcon(
                drawableResId = iconLeft,
                size = iconSize,
                tint = contentColor,
            )
        }

        if (label != null) {
            AutoSizingText(
                text = label,
                style = Theme.brockmann.button.semibold.small,
                color = contentColor,
            )
        }

        if (iconRight != null) {
            UiIcon(
                drawableResId = iconRight,
                size = iconSize,
                tint = contentColor,
            )
        }
    }
}

@Preview
@Composable
private fun VsButtonPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VsButton(
            label = "Primary",
            variant = Primary,
            state = Enabled,
            size = Medium,
            iconLeft = R.drawable.ic_caret_left,
            iconRight = R.drawable.ic_caret_right,
            onClick = {}
        )
        VsButton(
            label = "Primary Default",
            variant = Primary,
            state = Default,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Secondary Default",
            variant = Secondary,
            state = Default,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Primary Disabled",
            variant = Primary,
            state = Disabled,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Secondary",
            variant = Secondary,
            state = Enabled,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Secondary Disabled",
            variant = Secondary,
            state = Disabled,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Error",
            variant = Error,
            size = Medium,
            onClick = {}
        )

        VsButton(
            label = "Primary Enabled Small",
            variant = Primary,
            state = Enabled,
            size = Small,
            onClick = {}
        )

        VsButton(
            label = "Primary Mini Small",
            variant = Primary,
            state = Enabled,
            size = Mini,
            onClick = {}
        )

        VsButton(
            label = "Tertiary Mini Small",
            variant = Tertiary,
            state = Enabled,
            onClick = {}
        )

        VsButton(
            label = "Tertiary Mini Small",
            variant = Tertiary,
            state = Disabled,
            onClick = {}
        )

        VsButton(
            label = "Tertiary Mini Small",
            variant = Tertiary,
            state = Default,
            onClick = {}
        )


    }
}