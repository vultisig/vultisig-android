package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.vultisig.wallet.ui.screens.v2.home.components.rememberIsReduceMotionEnabled
import java.util.concurrent.atomic.AtomicBoolean

// A snappy roll, as iOS's `.snappy(duration: 0.3)` on its amount field.
private const val TYPED_ROLL_DURATION_MS = 300

/** Which end of the text keeps its characters' identity when the length changes. */
internal enum class RollingAnchor {
    /** A right-aligned figure: the cents stay put when the figure gains a digit. */
    End,

    /**
     * Typed text that grows on the right: leading characters stay put, an appended character rolls
     * in, a deleted one leaves, and a change in the middle rolls only the positions that changed.
     * Character widths change at once, so the glyphs stay over the field they mirror.
     */
    Start,
}

/**
 * Draws [text] one character at a time, rolling a character up or down when it changes; with
 * reduced motion characters swap in place. [anchor] decides which characters keep their identity
 * when the length changes. With [charX], each character is placed at that x offset (the glyph
 * positions of the text it mirrors) instead of side by side, so per-glyph layout cannot drift.
 */
@Composable
internal fun RollingText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    anchor: RollingAnchor = RollingAnchor.End,
    charX: ((index: Int) -> Int)? = null,
) {
    val isReduceMotion = rememberIsReduceMotionEnabled()
    // First-draw characters are already shown; a plain flag, so flipping it never recomposes.
    val isFirstComposition = remember { AtomicBoolean(true) }
    SideEffect { isFirstComposition.set(false) }

    val characters: @Composable () -> Unit = {
        text.forEachIndexed { index, char ->
            when (anchor) {
                RollingAnchor.End ->
                    key(text.length - index) {
                        RollingChar(char, color, style, isReduceMotion, isTyped = false)
                    }
                RollingAnchor.Start ->
                    key(index) {
                        val appear = remember {
                            val isAlreadyShown = isFirstComposition.get() || isReduceMotion
                            MutableTransitionState(isAlreadyShown).apply { targetState = true }
                        }
                        AnimatedVisibility(
                            visibleState = appear,
                            enter =
                                slideInVertically(tween(TYPED_ROLL_DURATION_MS)) { it } +
                                    fadeIn(tween(TYPED_ROLL_DURATION_MS)),
                            exit = ExitTransition.None,
                        ) {
                            RollingChar(char, color, style, isReduceMotion, isTyped = true)
                        }
                    }
            }
        }
    }

    if (charX == null) {
        Row(modifier = modifier) { characters() }
    } else {
        Layout(content = characters, modifier = modifier) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val xs = placeables.indices.map(charX)
            val width = placeables.indices.maxOfOrNull { xs[it] + placeables[it].width } ?: 0
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width.coerceAtMost(constraints.maxWidth), height) {
                placeables.forEachIndexed { index, placeable -> placeable.place(xs[index], 0) }
            }
        }
    }
}

@Composable
private fun RollingChar(
    char: Char,
    color: Color,
    style: TextStyle,
    isReduceMotion: Boolean,
    isTyped: Boolean,
) {
    AnimatedContent(
        targetState = char,
        transitionSpec = {
            if (isReduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState > initialState) 1 else -1
                if (isTyped) {
                    val roll =
                        tween<IntOffset>(TYPED_ROLL_DURATION_MS, easing = FastOutSlowInEasing)
                    val fade = tween<Float>(TYPED_ROLL_DURATION_MS)
                    (slideInVertically(roll) { it * direction } + fadeIn(fade)) togetherWith
                        (slideOutVertically(roll) { -it * direction } + fadeOut(fade)) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                } else {
                    (slideInVertically { it * direction } + fadeIn()) togetherWith
                        (slideOutVertically { -it * direction } + fadeOut()) using
                        SizeTransform(clip = false)
                }
            }
        },
    ) { shown ->
        Text(text = shown.toString(), color = color, style = style)
    }
}
