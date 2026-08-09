package com.vultisig.wallet.ui.theme.v2

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One step of the app's corner-radius scale.
 *
 * A token carries the whole shape, not just a number, and it *is* a [Shape] — so it drops straight
 * into `shape =`, [androidx.compose.ui.draw.clip], `background(...)` and `border(...)` without a
 * call site ever naming a radius or a corner geometry. That is the point of the layer: changing the
 * app's corner geometry becomes an edit inside [Radii] rather than a sweep across every rounded
 * surface.
 *
 * Values live in [Radii]; this type is only the primitive, the same way a `TextStyle` is the
 * primitive behind the typography scale.
 */
@Immutable
sealed class Radius(
    /**
     * The shape this token describes.
     *
     * Deliberately a [CornerBasedShape] rather than a bare [Shape]: it keeps `copy(topStart = ...)`
     * available to the surfaces that round only some of their corners, so those sites can still
     * derive from the token instead of restating its number.
     */
    val shape: CornerBasedShape
) : Shape by shape {

    /** A numeric step on the scale. */
    @Immutable
    data class Fixed(
        /**
         * Radius in dp.
         *
         * Use this only where a [Shape] will not fit — a `Canvas` corner radius, a shadow, an inset
         * computed off the surface it sits in. Everywhere else pass the token itself, so the corner
         * geometry travels with the number.
         */
        val size: Dp
    ) : Radius(RoundedCornerShape(size))

    /**
     * Fully rounded, at every surface size the app can render.
     *
     * [CircleShape] is `RoundedCornerShape(percent = 50)` — it is defined relative to the surface,
     * so unlike the "big number" idiom it cannot stop being round on a large surface. The app's
     * fully-round literals (50, 70, 77, 88, 99, 100 dp and `percent = 50`) all mean this, and
     * collapse onto it.
     */
    @Immutable data object Pill : Radius(CircleShape)
}

/**
 * The app's corner-radius scale.
 *
 * The numeric steps are named by size, not by surface: `md` means "12 from the scale", nothing
 * more. Which surface class should use which step is documentation (see the comments below), not a
 * contract — that mapping is still being reconciled against the design file, and encoding it in the
 * token names would freeze a half-verified answer.
 *
 * [Radius.Pill] is the one semantic token, because "fully rounded" is not a number. The design file
 * expresses it as 50, 77 or 99 depending on the component and the app has 50, 70, 77, 88, 99 and
 * 100 in use; all of them mean the same thing, and the token collapses them so no call site imports
 * another magic number.
 *
 * **There is deliberately no sheet token, and the scale stops at 24.** The two sheet frames in the
 * design file measure 38 and 28, but both are stock design-kit components dropped into the file
 * rather than authored Vultisig surfaces — the 38 one is Apple's iOS 26 sheet, the 28 one is
 * Material 3's default. Neither number is ours to own. Adding a step for them would encode a
 * platform default as a design decision and invite a real sheet to diverge from the platform.
 *
 * Values are shared with iOS and Windows — identical numbers, units per platform. `md`, `lg` and
 * `xl` are read off rendered frames in Figma file `puB2fsVpPrBx3Sup7gaa3v`, section "Main View
 * (Mobile) - iOS 26": the banner is 24, the inner icon tile and the input field are 16, the asset
 * list row is 12, and the chip / coin logo / glass-close family is fully round. Radii are **not**
 * Figma variables — `get_variable_defs` on that section returns colours, fonts, a stroke width and
 * `dimensions/3 = 8`, so there is no authored radius scale to import. `xs` and `sm` are therefore
 * inferred from existing app usage and that `dimensions/3`, and are lower-confidence than the rest.
 */
@Immutable
data class Radii(
    /** 4 — progress tracks, skeleton bars, hairline chips. */
    val xs: Radius.Fixed = Radius.Fixed(4.dp),
    /** 8 — small inline tags. */
    val sm: Radius.Fixed = Radius.Fixed(8.dp),
    /** 12 — list rows, compact containers. */
    val md: Radius.Fixed = Radius.Fixed(12.dp),
    /** 16 — input fields, inner icon tiles. */
    val lg: Radius.Fixed = Radius.Fixed(16.dp),
    /** 24 — cards, banners, list containers. */
    val xl: Radius.Fixed = Radius.Fixed(24.dp),
    /** Fully rounded. */
    val pill: Radius = Radius.Pill,
)
