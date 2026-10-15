package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Where the selection pill sits, as a fractional tab index. Set [isTapPending] before a tap changes
 * the selection so the pill lands with a swing.
 */
@Stable
internal class TabIndicatorState(
    val position: () -> Float,
    highlightedIndex: State<Int>,
    tapPending: MutableState<Boolean>,
) {
    /** The tab the pill is closest to. */
    val highlightedIndex: Int by highlightedIndex

    var isTapPending: Boolean by tapPending
}

/**
 * Follows [indicatorPosition] when given (a pager's scroll), else animates to [activeIndex]. A tap
 * swings the pill past its tab and back, standing in for iOS's tap-spring overshoot that a pager
 * clamps at its ends.
 */
@Composable
internal fun rememberTabIndicator(
    activeIndex: Int,
    lastIndex: Int,
    indicatorPosition: (() -> Float)?,
    isReduceMotion: Boolean,
): TabIndicatorState {
    val restingPosition = remember { Animatable(activeIndex.toFloat()) }
    val wobble = remember { Animatable(0f) }
    var landingDirection by remember { mutableFloatStateOf(0f) }
    var landedIndex by remember { mutableIntStateOf(activeIndex) }
    val tapPending = remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        val direction = sign((activeIndex - landedIndex).toFloat())
        landedIndex = activeIndex
        val wasTapped = tapPending.value
        tapPending.value = false
        if (indicatorPosition == null) restingPosition.animateTo(activeIndex.toFloat())
        if (direction == 0f || isReduceMotion || !wasTapped) return@LaunchedEffect
        landingDirection = direction
        wobble.snapTo(0f)
        wobble.animateTo(LANDING_OVERSHOOT, tween(LANDING_OUT_MILLIS, easing = FastOutSlowInEasing))
        wobble.animateTo(0f, spring(dampingRatio = LANDING_DAMPING, stiffness = BOUNCY_STIFFNESS))
    }

    val position: () -> Float =
        remember(indicatorPosition, lastIndex) {
            {
                val base = indicatorPosition?.invoke() ?: restingPosition.value
                (base + landingDirection * wobble.value).coerceIn(
                    -PILL_OVERSHOOT,
                    lastIndex + PILL_OVERSHOOT,
                )
            }
        }
    val highlightedIndex =
        remember(position) { derivedStateOf { position().roundToInt().coerceIn(0, lastIndex) } }

    return remember(position, highlightedIndex) {
        TabIndicatorState(position, highlightedIndex, tapPending)
    }
}

/**
 * The highlight behind the selected tab, stretched and flattened between tabs. [position] is read
 * in layout and draw only, so a drag moves it without recomposing the bar.
 */
@Composable
internal fun SelectionPill(tabCount: Int, position: () -> Float, isReduceMotion: Boolean) {
    fun stretch(): Float = if (isReduceMotion) 0f else stretchAt(position())
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .layout { measurable, constraints ->
                    val tabWidth = constraints.maxWidth / tabCount
                    val width = (tabWidth * (1 + PILL_STRETCH * stretch())).roundToInt()
                    val placeable =
                        measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Grows from its centre, so the stretch reaches towards both tabs.
                        val x = position() * tabWidth - (width - tabWidth) / 2f
                        placeable.place(x.roundToInt(), 0)
                    }
                }
                .graphicsLayer { scaleY = 1 - PILL_SQUASH * stretch() }
                .clip(CircleShape)
                .background(PillColor)
                .shadow(
                    elevation = 1.dp,
                    shape = CircleShape,
                    spotColor = Theme.v2.colors.neutrals.n100.copy(alpha = 0.2f),
                    clip = true,
                )
    )
}

/** `0` when the pill rests on a tab, `1` halfway between two. */
private fun stretchAt(position: Float): Float = sin(PI.toFloat() * (position - floor(position)))

private val PillColor = Color(0xFF1e3250)

/** How much wider the pill grows halfway between two tabs. */
private const val PILL_STRETCH = 0.35f

/** How much flatter it gets there, so the stretch reads as liquid. */
private const val PILL_SQUASH = 0.12f

/** How far past the first or last tab the pill may travel, in tabs. */
private const val PILL_OVERSHOOT = 0.15f

/** How far past the tab the pill swings when a tap lands on another one, in tabs. */
private const val LANDING_OVERSHOOT = 0.12f

// iOS keyframes: `.snappy` out, `.bouncy` back; damping = 1 − bounce, stiffness = (2π / 0.5 s)².
private const val LANDING_OUT_MILLIS = 120
private const val LANDING_DAMPING = 0.5f
internal const val BOUNCY_STIFFNESS = 158f
