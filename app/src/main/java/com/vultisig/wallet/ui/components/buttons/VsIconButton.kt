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
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Disabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Secondary
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Mini
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Small
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun VsIconButton(
    modifier: Modifier = Modifier,
    icon: Int,
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
                color = if (size == Mini) Theme.colors.transparent else
                    when (state) {
                        Enabled -> when (variant) {
                            Primary -> Theme.colors.buttons.primary
                            Secondary -> Theme.colors.buttons.secondary
                        }

                        Disabled -> Theme.colors.buttons.disabled
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
        val contentColor =
            if (size == Mini) Theme.colors.text.button.light else
                when (state) {
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