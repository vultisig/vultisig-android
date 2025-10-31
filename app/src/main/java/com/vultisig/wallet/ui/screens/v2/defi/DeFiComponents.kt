package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun InfoItem(icon: Int, label: String, value: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(
                size = 16.dp,
                drawableResId = icon,
                contentDescription = null,
                tint = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(4.dp)

            Text(
                text = label,
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )
        }

        if (value != null) {
            UiSpacer(6.dp)

            Text(
                text = value,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.light,
            )
        }
    }
}


@Composable
fun ActionButton(
    title: String,
    icon: Int,
    background: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    contentColor: Color,
    iconCircleColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
            disabledContainerColor = background.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = if (enabled) {
            border
        } else {
            border?.let {
                BorderStroke(
                    width = it.width,
                    color = when (val brush = it.brush) {
                        is SolidColor -> brush.value.copy(alpha = 0.5f)
                        else -> Color.Gray.copy(alpha = 0.5f) // fallback for gradient brushes
                    }
                )
            }
        },
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }

        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, name = "Info Item - With Value")
@Composable
private fun InfoItemWithValuePreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        InfoItem(
            icon = R.drawable.coins_tier,
            label = "APY",
            value = "12.5%"
        )
    }
}

@Preview(showBackground = true, name = "Info Item - Without Value")
@Composable
private fun InfoItemWithoutValuePreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        InfoItem(
            icon = R.drawable.calendar_days,
            label = "Next Churn",
            value = null
        )
    }
}

@Preview(showBackground = true, name = "Info Items - Row")
@Composable
private fun InfoItemsRowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "APY",
                value = "12.5%"
            )
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "Bonded",
                value = "1000 RUNE"
            )
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "Next Award",
                value = "20 RUNE"
            )
        }
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Enabled)")
@Composable
private fun ActionButtonBondEnabledPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.colors.buttons.primary,
            contentColor = Theme.colors.text.primary,
            iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
            enabled = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Disabled)")
@Composable
private fun ActionButtonBondDisabledPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.colors.buttons.primary,
            contentColor = Theme.colors.text.primary,
            iconCircleColor = Theme.colors.text.primary,
            enabled = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Unbond")
@Composable
private fun ActionButtonUnbondPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Unbond",
            icon = R.drawable.circle_minus,
            background = Color.Transparent,
            border = BorderStroke(1.dp, Theme.colors.buttons.primary),
            contentColor = Theme.colors.buttons.primary,
            iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Buttons - Row")
@Composable
private fun ActionButtonsRowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = "Bond",
                icon = R.drawable.circle_plus,
                background = Theme.colors.buttons.primary,
                contentColor = Theme.colors.text.primary,
                iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            ActionButton(
                title = "Unbond",
                icon = R.drawable.circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.colors.buttons.primary),
                contentColor = Theme.colors.buttons.primary,
                iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Complete Node Card Mock")
@Composable
private fun CompleteNodeCardMockPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Theme.colors.backgrounds.secondary,
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            // Node Address
            Text(
                text = "thor1abcd...xyz",
                style = Theme.brockmann.body.m.medium,
                color = Theme.colors.text.primary
            )
            
            UiSpacer(12.dp)
            
            // Info Items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "APY",
                    value = "12.5%"
                )
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "Bonded",
                    value = "1000 RUNE"
                )
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "Next Award",
                    value = "20 RUNE"
                )
            }
            
            UiSpacer(16.dp)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    title = "Bond",
                    icon = R.drawable.circle_plus,
                    background = Theme.colors.buttons.primary,
                    contentColor = Theme.colors.text.primary,
                    iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                ActionButton(
                    title = "Unbond",
                    icon = R.drawable.circle_minus,
                    background = Color.Transparent,
                    border = BorderStroke(1.dp, Theme.colors.buttons.primary),
                    contentColor = Theme.colors.buttons.primary,
                    iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }
    }
}