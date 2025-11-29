package com.vultisig.wallet.ui.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun VsHoldableButton(
    modifier: Modifier = Modifier,
    label: String? = null,
    holdDuration: Long = 800,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val backgroundColor = Theme.v2.colors.primary.accent3
    val fillColor = Theme.v2.colors.primary.accent5

    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isLongPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val longClickJob = scope.launch {
                        try {
                            progress.animateTo(
                                1f,
                                tween(
                                    holdDuration.toInt(),
                                    easing = LinearEasing
                                )
                            )
                            if (progress.value >= 1f) {
                                isLongPressed = true
                                onLongClick()
                            }
                        } catch (e: Exception) {
                            Timber.w(
                                e,
                                "Animation cancelled",

                                )
                        }
                    }

                    val up = waitForUpOrCancellation()

                    if (up != null && !isLongPressed) {
                        if (progress.value < 0.25f) {
                            onClick()
                        }
                    }

                    scope.launch {
                        progress.snapTo(0f)
                        longClickJob.cancelAndJoin()
                        isLongPressed = false
                    }
                }
            }
            .background(
                backgroundColor,
                RoundedCornerShape(percent = 100)
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(percent = 100))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(fillColor)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 14.dp,
                    horizontal = 32.dp
                )
        ) {
            val contentColor = Theme.v2.colors.text.primary
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
        onLongClick = {},
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    )
}