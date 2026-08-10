package com.vultisig.wallet.ui.theme.v2

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Pins the corner-radius scale, and pins the geometry the tokens produce against the numeric
 * literals they replace. A change to either is then a deliberate, visible edit rather than a silent
 * restyle of every surface that reads a token.
 */
internal class RadiusTokenTest {

    private val radius = V2.radius

    // 1dp == 1px keeps the assertions below readable.
    private val density = Density(density = 1f)

    /** Wide and tall enough that no token on the numeric scale is clamped by it. */
    private val surface = Size(width = 320f, height = 120f)

    @Test
    fun `scale values match the design scale`() {
        radius.xs.size shouldBe 4.dp
        radius.sm.size shouldBe 8.dp
        radius.md.size shouldBe 12.dp
        radius.lg.size shouldBe 16.dp
        radius.xl.size shouldBe 24.dp
    }

    @Test
    fun `numeric scale is strictly increasing`() {
        val scale = numericScale.map { it.size }
        scale shouldBe scale.sorted()
        scale.toSet().size shouldBe scale.size
    }

    /**
     * The tokens have to render exactly what the untokenised call sites render, or migrating a site
     * is a visual change rather than a rename. Every literal in the app spells the radius out as
     * `RoundedCornerShape(n.dp)`, so this compares each token's outline against exactly that.
     */
    @Test
    fun `token outlines match the literals they replace`() {
        val migrated =
            listOf(
                radius.xs to 4.dp,
                radius.sm to 8.dp,
                radius.md to 12.dp,
                radius.lg to 16.dp,
                radius.xl to 24.dp,
            )

        migrated.forEach { (token, literal) ->
            token.outline(surface) shouldBe RoundedCornerShape(literal).outline(surface)
        }
    }

    /**
     * `pill` is [CircleShape], which is `RoundedCornerShape(percent = 50)` — defined relative to
     * the surface rather than as a large constant, so unlike the "big number" idiom it cannot stop
     * being round on a large surface. The sizes below bracket everything the app can render.
     */
    @Test
    fun `pill is fully round at every size the app can render`() {
        val sizes =
            listOf(
                Size(320f, 44f), // search field
                Size(100f, 52f), // chip / counter row
                Size(56f, 56f), // circular icon button
                Size(1366f, 1024f), // tablet landscape
            )

        sizes.forEach { size ->
            radius.pill.drawnRadiusOn(size) shouldBe (minOf(size.width, size.height) / 2f)
        }
    }

    /**
     * The app expresses "fully round" as 50, 70, 77, 88, 99 and 100dp as well as `percent = 50`.
     * Compose scales adjacent corner radii down until they fit the surface, so all of those
     * collapse onto the same capsule — which is what lets one semantic token replace every one of
     * them without moving a pixel.
     */
    @Test
    fun `pill renders the same capsule as every fully-round literal in use`() {
        val searchFieldSized = Size(width = 320f, height = 44f)
        val capsule = radius.pill.drawnRadiusOn(searchFieldSized)

        capsule shouldBe 22f
        listOf(50.dp, 70.dp, 77.dp, 88.dp, 99.dp, 100.dp).forEach { literal ->
            // The clamp divides the surface by the literal, so the result lands a rounding error
            // away from the capsule rather than exactly on it. That difference is sub-pixel and not
            // what the migration turns on.
            RoundedCornerShape(literal).drawnRadiusOn(searchFieldSized) shouldBe
                (capsule plusOrMinus 0.001f)
        }
        RoundedCornerShape(percent = 50).drawnRadiusOn(searchFieldSized) shouldBe capsule
        RoundedCornerShape(50).drawnRadiusOn(searchFieldSized) shouldBe capsule
        listOf(60.dp, 999.dp).forEach { literal ->
            RoundedCornerShape(literal).drawnRadiusOn(searchFieldSized) shouldBe
                (capsule plusOrMinus 0.001f)
        }
    }

    /**
     * `percent = 100` is the one fully-round spelling that is not obviously equivalent, because
     * Compose does not scale the four corners by a common factor — it clamps each to the surface's
     * smaller dimension and then clamps the second corner of each edge to what is left of it.
     * Asking for 100% therefore takes the whole smaller dimension and leaves the opposite corner
     * nothing, which is a very different shape from asking for half of it twice.
     *
     * It survives that only because the clamp runs before the outline is built: both corners on the
     * short edge want the full dimension, and the leftover rule splits it evenly between them. The
     * app's primary button is drawn this way, so this is measured rather than assumed.
     */
    @Test
    fun `percent = 100 collapses onto the same capsule as pill`() {
        val buttonSized = Size(width = 328f, height = 48f)

        RoundedCornerShape(percent = 100).drawnRadiusOn(buttonSized) shouldBe
            radius.pill.drawnRadiusOn(buttonSized)
    }

    /**
     * The other spelling of "fully round", and the one a census misses: a number that is not
     * obviously large, on a surface small enough that it still clamps. Each pair below is a real
     * call site this migration collapsed onto `pill` — a carousel item, the limit-swap unit toggle,
     * the referral active dot and a sheet grabber. None of them reads as a fully-round literal, and
     * all four draw a capsule.
     */
    @Test
    fun `a literal over half the surface is already a capsule`() {
        val clampedSites =
            listOf(
                30.dp to Size(width = 150f, height = 50f),
                18.dp to Size(width = 32f, height = 32f),
                3.dp to Size(width = 6f, height = 6f),
                100.dp to Size(width = 36f, height = 4f),
            )

        clampedSites.forEach { (literal, size) ->
            RoundedCornerShape(literal).drawnRadiusOn(size) shouldBe radius.pill.drawnRadiusOn(size)
        }
    }

    /**
     * The bound that governs which fully-round literals may become `pill`, measured rather than
     * assumed: a literal only clamps to a capsule while the surface's smaller dimension stays under
     * twice the literal. Above that it draws its own number and is *not* a capsule, so migrating it
     * to `pill` would be a visual change. This is why the phase-1 slice only converts fully-round
     * literals on surfaces that are provably bounded.
     */
    @Test
    fun `a fully-round literal stops being a capsule once the surface out-runs it`() {
        val tall = Size(width = 320f, height = 240f) // 240 > 2 x 99

        RoundedCornerShape(99.dp).drawnRadiusOn(tall) shouldBe 99f
        radius.pill.drawnRadiusOn(tall) shouldBe 120f
    }

    @Test
    fun `pill is not a step on the numeric scale`() {
        numericScale.forEach { step -> radius.pill shouldNotBe step }
    }

    /**
     * Surfaces that round only some of their corners derive from the token's shape instead of
     * restating its number, so a change to the scale still reaches them.
     */
    @Test
    fun `a partially rounded shape derived from a token matches the literal it replaces`() {
        val topCornersOnly =
            radius.lg.shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

        topCornersOnly.cornersOn(surface) shouldBe
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp).cornersOn(surface)
        topCornersOnly.cornersOn(surface) shouldBe listOf(16f, 16f, 0f, 0f)
    }

    /**
     * A token is itself a [Shape], which is what lets a call site pass it straight to `shape =`,
     * `clip` or `background` without naming any geometry. That has to stay in step with the [shape]
     * it exposes for the derive-from-token cases above.
     */
    @Test
    fun `a token draws the same outline as the shape it exposes`() {
        allTokens.forEach { token -> token.outline(surface) shouldBe token.shape.outline(surface) }
    }

    private val numericScale: List<Radius.Fixed>
        get() = listOf(radius.xs, radius.sm, radius.md, radius.lg, radius.xl)

    private val allTokens: List<Radius>
        get() = numericScale + radius.pill

    private fun Shape.outline(size: Size): Outline =
        createOutline(size, LayoutDirection.Ltr, density)

    /**
     * The radius Compose actually draws at [size], after the fit-to-surface clamping that turns a
     * large radius into a capsule. Only valid for shapes whose four corners are equal.
     */
    private fun Shape.drawnRadiusOn(size: Size): Float =
        (outline(size) as Outline.Rounded).roundRect.topLeftCornerRadius.x

    /**
     * The four corner radii in `topStart, topEnd, bottomEnd, bottomStart` order, read before
     * clamping — so callers must pass a [size] large enough that no clamping applies. Reading them
     * off the shape rather than off an [Outline] keeps this a plain JVM test: an outline with
     * unequal corners builds an `android.graphics.Path`, which is not available here.
     */
    private fun CornerBasedShape.cornersOn(size: Size): List<Float> =
        listOf(topStart, topEnd, bottomEnd, bottomStart).map { it.toPx(size, density) }
}
