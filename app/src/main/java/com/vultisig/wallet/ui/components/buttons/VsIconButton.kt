package com.vultisig.wallet.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsIconButtonSize.*
import com.vultisig.wallet.ui.components.buttons.VsIconButtonState.*
import com.vultisig.wallet.ui.components.buttons.VsIconButtonVariant.*
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

enum class VsIconButtonVariant {
    Primary, Secondary,
}

enum class VsIconButtonState {
    Enabled, Disabled
}

enum class VsIconButtonSize {
    Medium, Small, Mini
}

@Composable
fun VsIconButton(
    modifier: Modifier = Modifier,
    icon: Int,
    variant: VsIconButtonVariant = Primary,
    state: VsIconButtonState = Enabled,
    size: VsIconButtonSize = Medium,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .background(
                color =
                    when (state) {
                        Enabled -> when (variant) {
                            Primary -> Theme.colors.buttons.primary
                            Secondary -> Theme.colors.backgrounds.secondary
                        }

                        Disabled -> when (variant) {
                            Primary -> Theme.colors.buttons.disabled
                            Secondary -> Theme.colors.buttons.disabled
                        }
                    },
                shape = RoundedCornerShape(percent = 100)
            )
            .clickable(enabled = state == Enabled, onClick = clickOnce(onClick = onClick))
            .then(
                when (size) {
                    Medium -> Modifier.padding(
                        vertical = 14.dp,
                        horizontal = 32.dp
                    )

                    Small -> Modifier.padding(
                        vertical = 12.dp,
                        horizontal = 12.dp
                    )

                    Mini -> Modifier
                }
            )

    ) {
        val contentColor = when (state) {
            Enabled -> when (variant) {
                Primary -> Theme.colors.backgrounds.primary
                Secondary -> Theme.colors.text.button.light
            }

            Disabled -> Theme.colors.text.button.disabled
        }

        val iconSize = when (size) {
            Medium -> 20.dp
            Small, Mini -> 16.dp
        }

        UiIcon(
            drawableResId = icon,
            size = iconSize,
            tint = contentColor,
        )
    }
}

@Preview
@Composable
private fun VsIconButtonPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VsIconButton(
            variant = Primary,
            state = Enabled,
            size = Medium,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Primary,
            state = Disabled,
            size = Medium,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Secondary,
            state = Enabled,
            size = Medium,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Secondary,
            state = Disabled,
            size = Medium,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Primary,
            state = Enabled,
            size = Small,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Primary,
            state = Disabled,
            size = Small,
            icon = R.drawable.ic_caret_left,
            onClick = {},
            )

        VsIconButton(
            variant = Primary,
            state = Enabled,
            size = Mini,
            icon = R.drawable.ic_caret_left,
            onClick = {},
        )
    }
}