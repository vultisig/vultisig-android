package com.vultisig.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.core.graphics.toColorInt

@Composable
fun OnBoardingComposeTheme(
    content: @Composable () -> Unit,
) {
    AppUtils(
        appColor = Colors.Default,
        menloTypography = menloTypography,
        montserratTypography = montserratTypography,
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                background = Color("#02122a".toColorInt()),
                onBackground = Color.White,
                primary = Color("#33e6bf".toColorInt()),
                onPrimary = Color("#02122a".toColorInt()),
                surfaceDim = Color("#1f9183".toColorInt())
            ),
            shapes = Shapes,
            content = content
        )
    }
}


internal object Theme {
    val colors: Colors
        @Composable
        get() = LocalAppColors.current

    val menlo: VultisigTypography
        @Composable
        get() = LocalMenloFamilyTypography.current

    val montserrat: VultisigTypography
        @Composable
        get() = LocalMontserratFamilyTypography.current

}


internal val Theme.cursorBrush: Brush
    @Composable
    get() = SolidColor(colors.neutral100)