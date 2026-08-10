package com.vultisig.wallet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.v2.V2

// Material 3 types these as CornerBasedShape, so they read the token's `shape` rather than the
// token itself. `large` is square — a zero corner is the absence of a radius, not a step on the
// scale, so it has no token to reference.
val Shapes: Shapes =
    Shapes(
        small = V2.radius.xs.shape,
        medium = V2.radius.xs.shape,
        large = RoundedCornerShape(0.dp),
    )
