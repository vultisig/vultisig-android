package com.vultisig.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.core.graphics.toColorInt
import com.vultisig.wallet.ui.theme.v2.LocalV2Theme
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.theme.v2.V2.colors

@Composable
fun OnBoardingComposeTheme(
    content: @Composable () -> Unit,
) {
    AppUtils(
        appColor = colors,
        menloTypography = menloTypography,
        montserratTypography = montserratTypography,
        brockmannTypography = brockmannTypography,
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                background = Theme.v2.colors.backgrounds.primary,
                onBackground = Theme.v2.colors.neutrals.n50,
                primary = Theme.v2.colors.buttons.primary,
                onPrimary = Theme.v2.colors.backgrounds.primary,
                surfaceDim = Color("#1f9183".toColorInt())
            ),
            shapes = Shapes,
            content = content
        )
    }
}


internal object Theme {

    val v2: V2
    @Composable
    get() = LocalV2Theme.current

    val menlo: VultisigTypography
        @Composable
        get() = LocalMenloFamilyTypography.current

    val montserrat: VultisigTypography
        @Composable
        get() = LocalMontserratFamilyTypography.current

    val brockmann: VsTypography
        @Composable
        get() = LocalBrockmannFamilyTypography.current

    val satoshi: SatoshiTypography = satoshiTypography
}


internal val Theme.cursorBrush: Brush
    @Composable
    get() = SolidColor(v2.colors.neutrals.n100)