package com.vultisig.wallet.ui.components

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.EdgeEffect
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.checkScrollableContainerConstraints
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.InspectorValueInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import com.vultisig.wallet.ui.components.EdgeEffectCompat.distanceCompat
import com.vultisig.wallet.ui.components.EdgeEffectCompat.onAbsorbCompat
import com.vultisig.wallet.ui.components.EdgeEffectCompat.onPullDistanceCompat
import com.vultisig.wallet.ui.components.EdgeEffectCompat.onReleaseWithOppositeDelta
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun rememberCustomOverscrollEffect(): OverscrollEffect {
    val context = LocalContext.current
    val config = LocalOverscrollConfiguration.current
    return if (config != null) {
        remember(context, config) { AndroidEdgeEffectOverscrollEffect(context, config) }
    } else {
        NoOpOverscrollEffect
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object NoOpOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset = performScroll(delta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) { performFling(velocity) }

    override val isInProgress: Boolean
        get() = false

    override val effectModifier: Modifier
        get() = Modifier
}


private typealias NativeCanvas = android.graphics.Canvas

private class DrawOverscrollModifier(
    private val overscrollEffect: AndroidEdgeEffectOverscrollEffect,
    inspectorInfo: InspectorInfo.() -> Unit,
) : DrawModifier, InspectorValueInfo(inspectorInfo) {

    override fun ContentDrawScope.draw() {
        drawContent()
        with(overscrollEffect) {
            drawOverscroll()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawOverscrollModifier) return false

        return overscrollEffect == other.overscrollEffect
    }

    override fun hashCode(): Int {
        return overscrollEffect.hashCode()
    }

    override fun toString(): String {
        return "DrawOverscrollModifier(overscrollEffect=$overscrollEffect)"
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class AndroidEdgeEffectOverscrollEffect(
    context: Context,
    private val overscrollConfig: OverscrollConfiguration,
) : OverscrollEffect {
    private var pointerPosition: Offset? = null

    private val topEffect = EdgeEffectCompat.create(context, null)
    private val bottomEffect = EdgeEffectCompat.create(context, null)
    private val leftEffect = EdgeEffectCompat.create(context, null)
    private val rightEffect = EdgeEffectCompat.create(context, null)
    private val allEffects = listOf(leftEffect, topEffect, rightEffect, bottomEffect)

    // hack explanation: those edge effects are used to negate the previous effect
    // of the corresponding edge
    // used to mimic the render node reset that is not available in the platform
    private val topEffectNegation = EdgeEffectCompat.create(context, null)
    private val bottomEffectNegation = EdgeEffectCompat.create(context, null)
    private val leftEffectNegation = EdgeEffectCompat.create(context, null)
    private val rightEffectNegation = EdgeEffectCompat.create(context, null)

    init {
        allEffects.fastForEach { it.color = overscrollConfig.glowColor.toArgb() }
    }

    // TODO replace with mutableStateOf(Unit, neverEqualPolicy()) after b/291647821 is addressed
    private var consumeCount = -1
    private var invalidateCount by mutableIntStateOf(0)

    @VisibleForTesting
    internal var invalidationEnabled = true

    private var scrollCycleInProgress: Boolean = false

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Early return
        if (containerSize.isEmpty()) {
            return performScroll(delta)
        }

        if (!scrollCycleInProgress) {
            stopOverscrollAnimation()
            scrollCycleInProgress = true
        }
        val pointer = pointerPosition ?: containerSize.center
        val consumedPixelsY = when {
            delta.y == 0f -> 0f
            topEffect.distanceCompat != 0f -> {
                pullTop(delta, pointer).also {
                    if (topEffect.distanceCompat == 0f) topEffect.onRelease()
                }
            }

            bottomEffect.distanceCompat != 0f -> {
                pullBottom(delta, pointer).also {
                    if (bottomEffect.distanceCompat == 0f) bottomEffect.onRelease()
                }
            }

            else -> 0f
        }
        val consumedPixelsX = when {
            delta.x == 0f -> 0f
            leftEffect.distanceCompat != 0f -> {
                pullLeft(delta, pointer).also {
                    if (leftEffect.distanceCompat == 0f) leftEffect.onRelease()
                }
            }

            rightEffect.distanceCompat != 0f -> {
                pullRight(delta, pointer).also {
                    if (rightEffect.distanceCompat == 0f) rightEffect.onRelease()
                }
            }

            else -> 0f
        }
        val consumedOffset = Offset(consumedPixelsX, consumedPixelsY)
        if (consumedOffset != Offset.Zero) invalidateOverscroll()

        val leftForDelta = delta - consumedOffset
        val consumedByDelta = performScroll(leftForDelta)
        val leftForOverscroll = leftForDelta - consumedByDelta

        var needsInvalidation = false
        if (source == NestedScrollSource.Drag) {
            // Ignore small deltas (< 0.5) as this usually comes from floating point rounding issues
            // and can cause scrolling to lock up (b/265363356)
            val appliedHorizontalOverscroll = if (leftForOverscroll.x > 0.5f) {
                pullLeft(leftForOverscroll, pointer)
                true
            } else if (leftForOverscroll.x < -0.5f) {
                pullRight(leftForOverscroll, pointer)
                true
            } else {
                false
            }
            val appliedVerticalOverscroll = if (leftForOverscroll.y > 0.5f) {
                pullTop(leftForOverscroll, pointer)
                true
            } else if (leftForOverscroll.y < -0.5f) {
                pullBottom(leftForOverscroll, pointer)
                true
            } else {
                false
            }
            needsInvalidation = appliedHorizontalOverscroll || appliedVerticalOverscroll
        }
        needsInvalidation = releaseOppositeOverscroll(delta) || needsInvalidation
        if (needsInvalidation) invalidateOverscroll()

        return consumedOffset + consumedByDelta
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        // Early return
        if (containerSize.isEmpty()) {
            performFling(velocity)
            return
        }
        val consumedX = if (velocity.x > 0f && leftEffect.distanceCompat != 0f) {
            leftEffect.onAbsorbCompat(velocity.x.roundToInt())
            velocity.x
        } else if (velocity.x < 0 && rightEffect.distanceCompat != 0f) {
            rightEffect.onAbsorbCompat(-velocity.x.roundToInt())
            velocity.x
        } else {
            0f
        }
        val consumedY = if (velocity.y > 0f && topEffect.distanceCompat != 0f) {
            topEffect.onAbsorbCompat(velocity.y.roundToInt())
            velocity.y
        } else if (velocity.y < 0f && bottomEffect.distanceCompat != 0f) {
            bottomEffect.onAbsorbCompat(-velocity.y.roundToInt())
            velocity.y
        } else {
            0f
        }
        val consumed = Velocity(consumedX, consumedY)
        if (consumed != Velocity.Zero) invalidateOverscroll()

        val remainingVelocity = velocity - consumed
        val consumedByVelocity = performFling(remainingVelocity)
        val leftForOverscroll = remainingVelocity - consumedByVelocity

        scrollCycleInProgress = false
        if (leftForOverscroll.x > 0) {
            leftEffect.onAbsorbCompat(leftForOverscroll.x.roundToInt())
        } else if (leftForOverscroll.x < 0) {
            rightEffect.onAbsorbCompat(-leftForOverscroll.x.roundToInt())
        }
        if (leftForOverscroll.y > 0) {
            topEffect.onAbsorbCompat(leftForOverscroll.y.roundToInt())
        } else if (leftForOverscroll.y < 0) {
            bottomEffect.onAbsorbCompat(-leftForOverscroll.y.roundToInt())
        }
        if (leftForOverscroll != Velocity.Zero) invalidateOverscroll()
        animateToRelease()
    }

    private var containerSize = Size.Zero

    override val isInProgress: Boolean
        get() {
            return allEffects.fastAny { it.distanceCompat != 0f }
        }

    private fun stopOverscrollAnimation(): Boolean {
        var stopped = false
        val fakeDisplacement = containerSize.center // displacement doesn't matter here
        if (leftEffect.distanceCompat != 0f) {
            pullLeft(Offset.Zero, fakeDisplacement)
            stopped = true
        }
        if (rightEffect.distanceCompat != 0f) {
            pullRight(Offset.Zero, fakeDisplacement)
            stopped = true
        }
        if (topEffect.distanceCompat != 0f) {
            pullTop(Offset.Zero, fakeDisplacement)
            stopped = true
        }
        if (bottomEffect.distanceCompat != 0f) {
            pullBottom(Offset.Zero, fakeDisplacement)
            stopped = true
        }
        return stopped
    }

    private val onNewSize: (IntSize) -> Unit = { size ->
        val differentSize = size.toSize() != containerSize
        containerSize = size.toSize()
        if (differentSize) {
            topEffect.setSize(size.width, size.height)
            bottomEffect.setSize(size.width, size.height)
            leftEffect.setSize(size.height, size.width)
            rightEffect.setSize(size.height, size.width)

            topEffectNegation.setSize(size.width, size.height)
            bottomEffectNegation.setSize(size.width, size.height)
            leftEffectNegation.setSize(size.height, size.width)
            rightEffectNegation.setSize(size.height, size.width)
        }
        if (differentSize) {
            invalidateOverscroll()
            animateToRelease()
        }
    }

    private var pointerId: PointerId? = null

    override val effectModifier: Modifier = Modifier
        .then(StretchOverscrollNonClippingLayer)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pointerId = down.id
                pointerPosition = down.position
                do {
                    val pressedChanges = awaitPointerEvent().changes.fastFilter { it.pressed }
                    // If the same ID we are already tracking is down, use that. Otherwise, use
                    // the next down, to move the overscroll to the next pointer.
                    val change = pressedChanges
                        .fastFirstOrNull { it.id == pointerId } ?: pressedChanges.firstOrNull()
                    if (change != null) {
                        // Update the id if we are now tracking a new down
                        pointerId = change.id
                        pointerPosition = change.position
                    }
                } while (pressedChanges.isNotEmpty())
                pointerId = null
                // Explicitly not resetting the pointer position until the next down, so we
                // don't change any existing effects
            }
        }
        .onSizeChanged(onNewSize)
        .then(
            DrawOverscrollModifier(
                this@AndroidEdgeEffectOverscrollEffect,
                debugInspectorInfo {
                    name = "overscroll"
                    value = this@AndroidEdgeEffectOverscrollEffect
                })
        )

    fun DrawScope.drawOverscroll() {
        if (containerSize.isEmpty()) {
            return
        }
        this.drawIntoCanvas {
            consumeCount = invalidateCount // <-- value read to redraw if needed
            val canvas = it.nativeCanvas
            var needsInvalidate = false
            // each side workflow:
            // 1. reset what was draw in the past cycle, effectively clearing the effect
            // 2. Draw the effect on the edge
            // 3. Remember how much was drawn to clear in 1. in the next cycle
            if (leftEffectNegation.distanceCompat != 0f) {
                drawRight(leftEffectNegation, canvas)
                leftEffectNegation.finish()
            }
            if (!leftEffect.isFinished) {
                needsInvalidate = drawLeft(leftEffect, canvas) || needsInvalidate
                leftEffectNegation.onPullDistanceCompat(leftEffect.distanceCompat, 0f)
            }
            if (topEffectNegation.distanceCompat != 0f) {
                drawBottom(topEffectNegation, canvas)
                topEffectNegation.finish()
            }
            if (!topEffect.isFinished) {
                needsInvalidate = drawTop(topEffect, canvas) || needsInvalidate
                topEffectNegation.onPullDistanceCompat(topEffect.distanceCompat, 0f)
            }
            if (rightEffectNegation.distanceCompat != 0f) {
                drawLeft(rightEffectNegation, canvas)
                rightEffectNegation.finish()
            }
            if (!rightEffect.isFinished) {
                needsInvalidate = drawRight(rightEffect, canvas) || needsInvalidate
                rightEffectNegation.onPullDistanceCompat(rightEffect.distanceCompat, 0f)
            }
            if (bottomEffectNegation.distanceCompat != 0f) {
                drawTop(bottomEffectNegation, canvas)
                bottomEffectNegation.finish()
            }
            if (!bottomEffect.isFinished) {
                needsInvalidate = drawBottom(bottomEffect, canvas) || needsInvalidate
                bottomEffectNegation.onPullDistanceCompat(bottomEffect.distanceCompat, 0f)
            }
            if (needsInvalidate) invalidateOverscroll()
        }
    }

    private fun DrawScope.drawLeft(left: EdgeEffect, canvas: NativeCanvas): Boolean {
        val restore = canvas.save()
        canvas.rotate(270f)
        canvas.translate(
            -containerSize.height,
            overscrollConfig.drawPadding.calculateLeftPadding(layoutDirection).toPx()
        )
        val needsInvalidate = left.draw(canvas)
        canvas.restoreToCount(restore)
        return needsInvalidate
    }

    private fun DrawScope.drawTop(top: EdgeEffect, canvas: NativeCanvas): Boolean {
        val restore = canvas.save()
        canvas.translate(0f, overscrollConfig.drawPadding.calculateTopPadding().toPx())
        val needsInvalidate = top.draw(canvas)
        canvas.restoreToCount(restore)
        return needsInvalidate
    }

    private fun DrawScope.drawRight(right: EdgeEffect, canvas: NativeCanvas): Boolean {
        val restore = canvas.save()
        val width = containerSize.width.roundToInt()
        val rightPadding = overscrollConfig.drawPadding.calculateRightPadding(layoutDirection)
        canvas.rotate(90f)
        canvas.translate(0f, -width.toFloat() + rightPadding.toPx())
        val needsInvalidate = right.draw(canvas)
        canvas.restoreToCount(restore)
        return needsInvalidate
    }

    private fun DrawScope.drawBottom(bottom: EdgeEffect, canvas: NativeCanvas): Boolean {
        val restore = canvas.save()
        canvas.rotate(180f)
        val bottomPadding = overscrollConfig.drawPadding.calculateBottomPadding().toPx()
        canvas.translate(-containerSize.width, -containerSize.height + bottomPadding)
        val needsInvalidate = bottom.draw(canvas)
        canvas.restoreToCount(restore)
        return needsInvalidate
    }

    private fun invalidateOverscroll() {
        if (invalidationEnabled) {
            if (consumeCount == invalidateCount) {
                invalidateCount++
            }
        }
    }

    // animate the edge effects to 0 (no overscroll). Usually needed when the finger is up.
    private fun animateToRelease() {
        var needsInvalidation = false
        allEffects.fastForEach {
            it.onRelease()
            needsInvalidation = it.isFinished || needsInvalidation
        }
        if (needsInvalidation) invalidateOverscroll()
    }

    private fun releaseOppositeOverscroll(delta: Offset): Boolean {
        var needsInvalidation = false
        if (!leftEffect.isFinished && delta.x < 0) {
            leftEffect.onReleaseWithOppositeDelta(delta = delta.x)
            needsInvalidation = leftEffect.isFinished
        }
        if (!rightEffect.isFinished && delta.x > 0) {
            rightEffect.onReleaseWithOppositeDelta(delta = delta.x)
            needsInvalidation = needsInvalidation || rightEffect.isFinished
        }
        if (!topEffect.isFinished && delta.y < 0) {
            topEffect.onReleaseWithOppositeDelta(delta = delta.y)
            needsInvalidation = needsInvalidation || topEffect.isFinished
        }
        if (!bottomEffect.isFinished && delta.y > 0) {
            bottomEffect.onReleaseWithOppositeDelta(delta = delta.y)
            needsInvalidation = needsInvalidation || bottomEffect.isFinished
        }
        return needsInvalidation
    }

    private fun pullTop(scroll: Offset, displacement: Offset): Float {
        val displacementX: Float = displacement.x / containerSize.width
        val pullY = scroll.y / containerSize.height
        val consumed = topEffect.onPullDistanceCompat(pullY, displacementX) * containerSize.height
        // If overscroll is showing, assume we have consumed all the provided scroll, and return
        // that amount directly to avoid floating point rounding issues (b/265363356)
        return if (topEffect.distanceCompat != 0f) {
            scroll.y
        } else {
            consumed
        }
    }

    private fun pullBottom(scroll: Offset, displacement: Offset): Float {
        val displacementX: Float = displacement.x / containerSize.width
        val pullY = scroll.y / containerSize.height
        val consumed = -bottomEffect.onPullDistanceCompat(
            -pullY,
            1 - displacementX
        ) * containerSize.height
        // If overscroll is showing, assume we have consumed all the provided scroll, and return
        // that amount directly to avoid floating point rounding issues (b/265363356)
        return if (bottomEffect.distanceCompat != 0f) {
            scroll.y
        } else {
            consumed
        }
    }

    private fun pullLeft(scroll: Offset, displacement: Offset): Float {
        val displacementY: Float = displacement.y / containerSize.height
        val pullX = scroll.x / containerSize.width
        val consumed = leftEffect.onPullDistanceCompat(
            pullX,
            1 - displacementY
        ) * containerSize.width
        // If overscroll is showing, assume we have consumed all the provided scroll, and return
        // that amount directly to avoid floating point rounding issues (b/265363356)
        return if (leftEffect.distanceCompat != 0f) {
            scroll.x
        } else {
            consumed
        }
    }

    private fun pullRight(scroll: Offset, displacement: Offset): Float {
        val displacementY: Float = displacement.y / containerSize.height
        val pullX = scroll.x / containerSize.width
        val consumed = -rightEffect.onPullDistanceCompat(
            -pullX,
            displacementY
        ) * containerSize.width
        // If overscroll is showing, assume we have consumed all the provided scroll, and return
        // that amount directly to avoid floating point rounding issues (b/265363356)
        return if (rightEffect.distanceCompat != 0f) {
            scroll.x
        } else {
            consumed
        }
    }
}

internal val MaxSupportedElevation = 30.dp

/**
 * There is an unwanted behavior in the stretch overscroll effect we have to workaround:
 * When the effect is started it is getting the current RenderNode bounds and clips the content
 * by those bounds. Even if this RenderNode is not configured to do clipping. Or if it clips,
 * but not within its bounds, but by the outline provided which could have a completely different
 * bounds. That is what happens with our scrolling containers - they all clip by the rect which is
 * larger than the RenderNode bounds in order to not clip the shadows drawn in the cross axis of
 * the scrolling direction. This issue is not that visible in the Views world because Views do
 * clip by default. So adding one more clip doesn't change much. Thus why the whole shadows
 * mechanism in the Views world works differently, the shadows are drawn not in-place, but with
 * the background of the first parent which has a background.
 * In order to neutralize this unnecessary clipping we can use similar technique to what we
 * use in those scrolling container clipping by extending the layer size on some predefined
 * [MaxSupportedElevation] constant. In this case we have to solve that with two layout modifiers:
 * 1) the inner one will measure its measurable as previously, but report to the parent modifier
 * with added extra size.
 * 2) the outer modifier will position its measurable with the layer, so the layer size is
 * increased, and then report the measured size of its measurable without the added extra size.
 * With such approach everything is measured and positioned as before, but we introduced an
 * extra layer with the incremented size, which will be used by the overscroll effect and allows
 * to draw the content without clipping the shadows.
 */
private val StretchOverscrollNonClippingLayer: Modifier =
    // we only need to fix the layer size when the stretch overscroll is active (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val extraSizePx = (MaxSupportedElevation * 2).roundToPx()
                layout(
                    (placeable.measuredWidth - extraSizePx).coerceAtLeast(0),
                    (placeable.measuredHeight - extraSizePx).coerceAtLeast(0)
                ) {
                    // because this modifier report the size which is larger than the passed max
                    // constraints this larger box will be automatically centered within the
                    // constraints. we need to first add out offset and then neutralize the centering.
                    placeable.placeWithLayer(
                        -extraSizePx / 2 - (placeable.width - placeable.measuredWidth) / 2,
                        -extraSizePx / 2 - (placeable.height - placeable.measuredHeight) / 2
                    )
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val extraSizePx = (MaxSupportedElevation * 2).roundToPx()
                val width = placeable.width + extraSizePx
                val height = placeable.height + extraSizePx
                layout(width, height) {
                    placeable.place(extraSizePx / 2, extraSizePx / 2)
                }
            }
    } else {
        Modifier
    }


internal object EdgeEffectCompat {

    fun create(context: Context, attrs: AttributeSet?): EdgeEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31Impl.create(context, attrs)
        } else {
            GlowEdgeEffectCompat(context)
        }
    }

    fun EdgeEffect.onPullDistanceCompat(
        deltaDistance: Float,
        displacement: Float,
    ): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Api31Impl.onPullDistance(this, deltaDistance, displacement)
        }
        this.onPull(deltaDistance, displacement)
        return deltaDistance
    }

    fun EdgeEffect.onAbsorbCompat(velocity: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return this.onAbsorb(velocity)
        } else if (this.isFinished) { // only absorb the glow effect if it is not active (finished)
            this.onAbsorb(velocity)
        }
    }

    /**
     * Used for calls to [EdgeEffect.onRelease] that happen because of scroll delta in the opposite
     * direction to the overscroll. See [GlowEdgeEffectCompat].
     */
    fun EdgeEffect.onReleaseWithOppositeDelta(delta: Float) {
        if (this is GlowEdgeEffectCompat) {
            releaseWithOppositeDelta(delta)
        } else {
            onRelease()
        }
    }

    val EdgeEffect.distanceCompat: Float
        get() {
            return if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
                Api31Impl.getDistance(this)
            } else 0f
        }
}

/**
 * Compat class to work around a framework issue (b/242864658) - small negative deltas that release
 * an overscroll followed by positive deltas cause the glow overscroll effect to instantly
 * disappear. This can happen when you pull the overscroll, and keep it there - small fluctuations
 * in the pointer position can cause these small negative deltas, even though on average it is not
 * really moving. To workaround this we only release the overscroll if the cumulative negative
 * deltas are larger than a minimum value - this should catch the majority of cases.
 */
private class GlowEdgeEffectCompat(context: Context) : EdgeEffect(context) {
    // Minimum distance in the opposite scroll direction to trigger a release
    private val oppositeReleaseDeltaThreshold = with(Density(context)) { 1.dp.toPx() }
    private var oppositeReleaseDelta = 0f

    override fun onPull(deltaDistance: Float, displacement: Float) {
        oppositeReleaseDelta = 0f
        super.onPull(deltaDistance, displacement)
    }

    override fun onPull(deltaDistance: Float) {
        oppositeReleaseDelta = 0f
        super.onPull(deltaDistance)
    }

    override fun onRelease() {
        oppositeReleaseDelta = 0f
        super.onRelease()
    }

    override fun onAbsorb(velocity: Int) {
        oppositeReleaseDelta = 0f
        super.onAbsorb(velocity)
    }

    /**
     * Increments the current cumulative delta, and calls [onRelease] if it is greater than
     * [oppositeReleaseDeltaThreshold].
     */
    fun releaseWithOppositeDelta(delta: Float) {
        oppositeReleaseDelta += delta
        if (abs(oppositeReleaseDelta) > oppositeReleaseDeltaThreshold) {
            onRelease()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private object Api31Impl {
    @DoNotInline
    fun create(context: Context, attrs: AttributeSet?): EdgeEffect {
        return try {
            EdgeEffect(context, attrs)
        } catch (t: Throwable) {
            EdgeEffect(context) // Old preview release
        }
    }

    @DoNotInline
    fun onPullDistance(
        edgeEffect: EdgeEffect,
        deltaDistance: Float,
        displacement: Float,
    ): Float {
        return try {
            edgeEffect.onPullDistance(deltaDistance, displacement)
        } catch (t: Throwable) {
            edgeEffect.onPull(deltaDistance, displacement) // Old preview release
            0f
        }
    }

    @DoNotInline
    fun getDistance(edgeEffect: EdgeEffect): Float {
        return try {
            edgeEffect.getDistance()
        } catch (t: Throwable) {
            0f // Old preview release
        }
    }
}






@Stable
class ScrollState(initial: Int) : ScrollableState {

    /**
     * current scroll position value in pixels
     */
    var value: Int by mutableIntStateOf(initial)
        private set

    /**
     * maximum bound for [value], or [Int.MAX_VALUE] if still unknown
     */
    var maxValue: Int
        get() = _maxValueState.intValue
        internal set(newMax) {
            _maxValueState.intValue = newMax
            Snapshot.withoutReadObservation {
                if (value > newMax) {
                    value = newMax
                }
            }
        }

    /**
     * Size of the viewport on the scrollable axis, or 0 if still unknown. Note that this value
     * is only populated after the first measure pass.
     */
    var viewportSize: Int by mutableIntStateOf(0)
        internal set

    /**
     * [InteractionSource] that will be used to dispatch drag events when this
     * list is being dragged. If you want to know whether the fling (or smooth scroll) is in
     * progress, use [isScrollInProgress].
     */
    val interactionSource: InteractionSource get() = internalInteractionSource

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    private var _maxValueState = mutableIntStateOf(Int.MAX_VALUE)

    /**
     * We receive scroll events in floats but represent the scroll position in ints so we have to
     * manually accumulate the fractional part of the scroll to not completely ignore it.
     */
    private var accumulator: Float = 0f

    private val scrollableState = ScrollableState {
        val absolute = (value + it + accumulator)
        val newValue = absolute.coerceIn(0f, maxValue.toFloat())
        val changed = absolute != newValue
        val consumed = newValue - value
        val consumedInt = consumed.roundToInt()
        value += consumedInt
        accumulator = consumed - consumedInt

        // Avoid floating-point rounding error
        if (changed) consumed else it
    }

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ): Unit = scrollableState.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float =
        scrollableState.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean
        get() = scrollableState.isScrollInProgress

    override val canScrollForward: Boolean by derivedStateOf { true } // TODO value < maxValue }

    override val canScrollBackward: Boolean by derivedStateOf { true } // TODO value > 0 }

    /**
     * Scroll to position in pixels with animation.
     *
     * @param value target value in pixels to smooth scroll to, value will be coerced to
     * 0..maxPosition
     * @param animationSpec animation curve for smooth scroll animation
     */
    suspend fun animateScrollTo(
        value: Int,
        animationSpec: AnimationSpec<Float> = SpringSpec()
    ) {
        this.animateScrollBy((value - this.value).toFloat(), animationSpec)
    }

    /**
     * Instantly jump to the given position in pixels.
     *
     * Cancels the currently running scroll, if any, and suspends until the cancellation is
     * complete.
     *
     * @see animateScrollTo for an animated version
     *
     * @param value number of pixels to scroll by
     * @return the amount of scroll consumed
     */
    suspend fun scrollTo(value: Int): Float = this.scrollBy((value - this.value).toFloat())

    companion object {
        /**
         * The default [Saver] implementation for [ScrollState].
         */
        val Saver: Saver<ScrollState, *> = Saver(
            save = { it.value },
            restore = { ScrollState(it) }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
fun Modifier.scroll(
    state: ScrollState,
    reverseScrolling: Boolean,
    flingBehavior: FlingBehavior?,
    isScrollable: Boolean,
    isVertical: Boolean
) = composed(
    factory = {
        val overscrollEffect = rememberCustomOverscrollEffect() //ScrollableDefaults.overscrollEffect()
        val coroutineScope = rememberCoroutineScope()
        val semantics = Modifier.semantics {
            isTraversalGroup = true
            val accessibilityScrollState = ScrollAxisRange(
                value = { state.value.toFloat() },
                maxValue = { state.maxValue.toFloat() },
                reverseScrolling = reverseScrolling
            )
            if (isVertical) {
                this.verticalScrollAxisRange = accessibilityScrollState
            } else {
                this.horizontalScrollAxisRange = accessibilityScrollState
            }
            if (isScrollable) {
                // when b/156389287 is fixed, this should be proper scrollTo with reverse handling
                scrollBy(
                    action = { x: Float, y: Float ->
                        coroutineScope.launch {
                            if (isVertical) {
                                (state as ScrollableState).animateScrollBy(y)
                            } else {
                                (state as ScrollableState).animateScrollBy(x)
                            }
                        }
                        return@scrollBy true
                    }
                )
            }
        }
        val orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal
        val scrolling = Modifier.scrollable(
            orientation = orientation,
            reverseDirection = ScrollableDefaults.reverseDirection(
                LocalLayoutDirection.current,
                orientation,
                reverseScrolling
            ),
            enabled = isScrollable,
            interactionSource = null, // TODO state.internalInteractionSource,
            flingBehavior = flingBehavior,
            state = state,
            overscrollEffect = overscrollEffect
        )
        val layout =
            ScrollingLayoutElement(state, reverseScrolling, isVertical)
        semantics
            .clipScrollableContainer(orientation)
            .overscroll(overscrollEffect)
            .then(scrolling)
            .then(layout)
    },
    inspectorInfo = debugInspectorInfo {
        name = "scroll"
        properties["state"] = state
        properties["reverseScrolling"] = reverseScrolling
        properties["flingBehavior"] = flingBehavior
        properties["isScrollable"] = isScrollable
        properties["isVertical"] = isVertical
    }
)

internal class ScrollingLayoutElement(
    val scrollState: ScrollState,
    val isReversed: Boolean,
    val isVertical: Boolean
) : ModifierNodeElement<ScrollingLayoutNode>() {
    override fun create(): ScrollingLayoutNode {
        return ScrollingLayoutNode(
            scrollerState = scrollState,
            isReversed = isReversed,
            isVertical = isVertical
        )
    }

    override fun update(node: ScrollingLayoutNode) {
        node.scrollerState = scrollState
        node.isReversed = isReversed
        node.isVertical = isVertical
    }

    override fun hashCode(): Int {
        var result = scrollState.hashCode()
        result = 31 * result + isReversed.hashCode()
        result = 31 * result + isVertical.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ScrollingLayoutElement) return false
        return scrollState == other.scrollState &&
                isReversed == other.isReversed &&
                isVertical == other.isVertical
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layoutInScroll"
        properties["state"] = scrollState
        properties["isReversed"] = isReversed
        properties["isVertical"] = isVertical
    }
}

internal class ScrollingLayoutNode(
    var scrollerState: ScrollState,
    var isReversed: Boolean,
    var isVertical: Boolean
) : LayoutModifierNode, Modifier.Node() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        checkScrollableContainerConstraints(
            constraints,
            if (isVertical) Orientation.Vertical else Orientation.Horizontal
        )

        val childConstraints = constraints.copy(
            maxHeight = if (isVertical) Constraints.Infinity else constraints.maxHeight,
            maxWidth = if (isVertical) constraints.maxWidth else Constraints.Infinity
        )
        val placeable = measurable.measure(childConstraints)
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        val height = placeable.height.coerceAtMost(constraints.maxHeight)
        val scrollHeight = placeable.height - height
        val scrollWidth = placeable.width - width
        val side = if (isVertical) scrollHeight else scrollWidth
        // The max value must be updated before returning from the measure block so that any other
        // chained RemeasurementModifiers that try to perform scrolling based on the new
        // measurements inside onRemeasured are able to scroll to the new max based on the newly-
        // measured size.
        scrollerState.maxValue = side
        scrollerState.viewportSize = if (isVertical) height else width
        return layout(width, height) {
            val scroll = scrollerState.value.coerceIn(0, side)
            val absScroll = if (isReversed) scroll - side else -scroll
            val xOffset = if (isVertical) 0 else absScroll
            val yOffset = if (isVertical) absScroll else 0
            placeable.placeRelativeWithLayer(xOffset, yOffset)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (isVertical) {
            measurable.minIntrinsicWidth(Constraints.Infinity)
        } else {
            measurable.minIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (isVertical) {
            measurable.minIntrinsicHeight(width)
        } else {
            measurable.minIntrinsicHeight(Constraints.Infinity)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return if (isVertical) {
            measurable.maxIntrinsicWidth(Constraints.Infinity)
        } else {
            measurable.maxIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return if (isVertical) {
            measurable.maxIntrinsicHeight(width)
        } else {
            measurable.maxIntrinsicHeight(Constraints.Infinity)
        }
    }
}