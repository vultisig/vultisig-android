package com.vultisig.wallet.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Mini
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Small
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Disabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Secondary
import com.vultisig.wallet.ui.theme.Theme

enum class VsButtonVariant {
    Primary, Secondary
}

enum class VsButtonState {
    Enabled, Disabled
}

enum class VsButtonSize {
    Medium, Small, Mini
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
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .background(
                color = when (state) {
                    Enabled -> when (variant) {
                        Primary -> Theme.colors.buttons.primary
                        Secondary -> Theme.colors.buttons.secondary
                    }

                    Disabled -> Theme.colors.buttons.disabled
                },
                shape = RoundedCornerShape(percent = 100)
            )
            .clickable(enabled = state == Enabled, onClick = onClick)
            .then(
                when (size) {
                    Medium -> Modifier.padding(
                        vertical = 14.dp,
                        horizontal = 32.dp
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
        val contentColor = when (state) {
            Enabled -> when (variant) {
                Primary -> Theme.colors.text.button.dark
                Secondary -> Theme.colors.text.button.light
            }

            Disabled -> Theme.colors.text.button.disabled
        }

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
            Text(
                text = label,
                style = Theme.brockmann.button.semibold,
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
    }
}