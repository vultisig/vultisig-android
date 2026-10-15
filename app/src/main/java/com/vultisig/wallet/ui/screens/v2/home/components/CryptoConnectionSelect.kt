package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

val BOTH_CRYPTO_CONNECTION_TYPES = listOf(CryptoConnectionType.Wallet, CryptoConnectionType.Defi)

val ONLY_WALLET = listOf(CryptoConnectionType.Wallet)

/**
 * The Wallet / DeFi pill. [indicatorPosition] is where the selection sits as a fractional tab index
 * (whole numbers at rest, in between while a pager is dragged); without it the selection springs to
 * [activeType]. A tap that lands on a new [activeType] swings the pill past it and back, standing
 * in for the overshoot of iOS's tap spring, which a pager clamps at its ends; a swipe lands without
 * it. The newly highlighted icon bounces. Motion mirrors iOS `VultiTabBarControls` and
 * `HomePagerTabBar`.
 */
@Composable
internal fun CryptoConnectionSelect(
    modifier: Modifier = Modifier,
    activeType: CryptoConnectionType,
    availableCryptoTypes: List<CryptoConnectionType> = BOTH_CRYPTO_CONNECTION_TYPES,
    indicatorPosition: (() -> Float)? = null,
    onTypeClick: (CryptoConnectionType) -> Unit,
) {
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val activeIndex = availableCryptoTypes.indexOf(activeType).coerceAtLeast(0)
    val lastIndex = availableCryptoTypes.lastIndex

    val restingPosition = remember { Animatable(activeIndex.toFloat()) }
    val wobble = remember { Animatable(0f) }
    var landingDirection by remember { mutableFloatStateOf(0f) }
    var landedIndex by remember { mutableIntStateOf(activeIndex) }
    var isTapPending by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        val direction = sign((activeIndex - landedIndex).toFloat())
        landedIndex = activeIndex
        val wasTapped = isTapPending
        isTapPending = false
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
    val highlightedIndex by
        remember(position) { derivedStateOf { position().roundToInt().coerceIn(0, lastIndex) } }

    val c1 = Color(0xFF284570)
    val c2 = Theme.v2.colors.backgrounds.secondary
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(brush = Brush.sweepGradient(colors = listOf(c2, c1, c1, c1, c2, c2)))
                .padding(1.dp)
    ) {
        Box(
            modifier =
                modifier
                    .height(64.dp)
                    .width(TabWidth * availableCryptoTypes.size)
                    .clip(CircleShape)
                    .background(Color(0xFF0d2446))
                    .padding(all = 4.dp)
        ) {
            SelectionPill(
                tabCount = availableCryptoTypes.size,
                position = position,
                isReduceMotion = isReduceMotion,
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                availableCryptoTypes.forEachIndexed { index, type ->
                    WalletEarnOption(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isTapPending = index != activeIndex
                            onTypeClick(type)
                        },
                        text = type.label(),
                        icon = type.icon,
                        isHighlighted = index == highlightedIndex,
                        isBounceEnabled = !isReduceMotion,
                    )
                }
            }
        }
    }
}

/**
 * The highlight behind the selected tab, stretched and flattened between tabs. [position] is read
 * in layout and draw only, so a drag moves it without recomposing the bar.
 */
@Composable
private fun SelectionPill(tabCount: Int, position: () -> Float, isReduceMotion: Boolean) {
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .layout { measurable, constraints ->
                    val tabWidth = constraints.maxWidth / tabCount
                    val stretch = if (isReduceMotion) 0f else stretchAt(position())
                    val width = (tabWidth * (1 + PILL_STRETCH * stretch)).roundToInt()
                    val placeable =
                        measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Grows from its centre, so the stretch reaches towards both tabs.
                        val x = position() * tabWidth - (width - tabWidth) / 2f
                        placeable.place(x.roundToInt(), 0)
                    }
                }
                .graphicsLayer {
                    scaleY = 1 - PILL_SQUASH * if (isReduceMotion) 0f else stretchAt(position())
                }
                .clip(CircleShape)
                .background(Color(0xFF1e3250))
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

@Composable
private fun CryptoConnectionType.label(): String =
    when (this) {
        CryptoConnectionType.Wallet -> stringResource(R.string.wallet)
        CryptoConnectionType.Defi -> stringResource(R.string.defi)
    }

@get:DrawableRes
private val CryptoConnectionType.icon: Int
    get() =
        when (this) {
            CryptoConnectionType.Wallet -> R.drawable.wallet_filled
            CryptoConnectionType.Defi -> R.drawable.nodes
        }

@Composable
private fun WalletEarnOption(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    @DrawableRes icon: Int,
    isHighlighted: Boolean,
    isBounceEnabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconScale = remember { Animatable(1f) }
    var wasHighlighted by remember { mutableStateOf(isHighlighted) }

    LaunchedEffect(isHighlighted) {
        val isNewlyHighlighted = isHighlighted && !wasHighlighted
        wasHighlighted = isHighlighted
        if (!isNewlyHighlighted || !isBounceEnabled) return@LaunchedEffect
        iconScale.animateTo(ICON_BOUNCE_SCALE, tween(ICON_BOUNCE_OUT_MILLIS))
        iconScale.animateTo(
            1f,
            spring(dampingRatio = ICON_BOUNCE_DAMPING, stiffness = BOUNCY_STIFFNESS),
        )
    }

    Column(
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val contentColor =
            if (isHighlighted) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary
        val contentColorAnimated by animateColorAsState(contentColor)
        UiIcon(
            drawableResId = icon,
            size = 24.dp,
            tint = contentColorAnimated,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        )
        Text(
            text = text,
            style = Theme.brockmann.supplementary.captionSmall,
            color = contentColorAnimated,
        )
    }
}

private val TabWidth = 92.dp

/** How much wider the pill grows halfway between two tabs. */
private const val PILL_STRETCH = 0.35f

/** How much flatter it gets there, so the stretch reads as liquid. */
private const val PILL_SQUASH = 0.12f

/** How far past the first or last tab the pill may travel, in tabs. */
private const val PILL_OVERSHOOT = 0.15f

/** How far past the tab the pill swings when a tap lands on another one, in tabs. */
private const val LANDING_OVERSHOOT = 0.12f

private const val ICON_BOUNCE_SCALE = 1.22f

// iOS keyframes: a `.snappy` spring out over 0.12–0.14 s, then `.bouncy` back. A SwiftUI spring's
// damping ratio is 1 − bounce, and `.bouncy(extraBounce:)` adds to its 0.3 bounce; stiffness is
// (2π / 0.5 s response)².
private const val LANDING_OUT_MILLIS = 120
private const val LANDING_DAMPING = 0.5f
private const val ICON_BOUNCE_OUT_MILLIS = 140
private const val ICON_BOUNCE_DAMPING = 0.45f
private const val BOUNCY_STIFFNESS = 158f

@Preview
@Composable
private fun PreviewWalletEarnSelect() {
    CryptoConnectionSelect(
        activeType = CryptoConnectionType.Defi,
        onTypeClick = {},
        availableCryptoTypes = BOTH_CRYPTO_CONNECTION_TYPES,
    )
}
