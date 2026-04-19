package com.vultisig.wallet.ui.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VsHoldableButton(
    modifier: Modifier = Modifier,
    label: String? = null,
    holdDuration: Long = LocalViewConfiguration.current.longPressTimeoutMillis,
    enabled: VsButtonState = VsButtonState.Enabled,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isButtonEnabled = enabled == VsButtonState.Enabled || enabled == VsButtonState.Default
    val backgroundColor =
        if (isButtonEnabled) {
            Theme.v2.colors.primary.accent3
        } else {
            Theme.v2.colors.primary.accent3.copy(alpha = 0.5f)
        }
    val fillColor =
        if (isButtonEnabled) {
            Theme.v2.colors.primary.accent5
        } else {
            Theme.v2.colors.primary.accent5.copy(alpha = 0.5f)
        }

    val progress = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }

    // Keep the progress fill aligned with the platform long-press timeout so the
    // visual confirmation completes when onLongClick actually fires.
    LaunchedEffect(interactionSource, holdDuration) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(holdDuration.toInt(), easing = LinearEasing))
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    progress.snapTo(0f)
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isButtonEnabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .background(backgroundColor, RoundedCornerShape(percent = 100))
    ) {
        Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(percent = 100))) {
            Box(
                modifier =
                    Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(fillColor)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 32.dp),
        ) {
            val contentColor =
                if (isButtonEnabled) {
                    Theme.v2.colors.text.primary
                } else {
                    Theme.v2.colors.text.primary.copy(alpha = 0.5f)
                }
            if (label != null) {
                Text(
                    text = label,
                    style = Theme.brockmann.button.semibold.semibold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview
@Composable
private fun VsHoldableButtonPreview() {
    VsHoldableButton(
        label = "Hold",
        enabled = VsButtonState.Enabled,
        onLongClick = {},
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun VsHoldableButtonDisabledPreview() {
    VsHoldableButton(
        label = "Hold (Disabled)",
        enabled = VsButtonState.Enabled,
        onLongClick = {},
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
}
