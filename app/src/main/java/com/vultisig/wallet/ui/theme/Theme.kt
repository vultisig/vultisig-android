package com.vultisig.wallet.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import com.vultisig.wallet.app.activity.MainActivity

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun OnBoardingComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = Colors.Default

    AppUtils(
        appColor = appColors,
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
    val localWindow = (LocalContext.current as Activity).window
    if (darkTheme) {
        localWindow.statusBarColor = appColors.oxfordBlue800.toArgb()
    } else {
        localWindow.statusBarColor = appColors.oxfordBlue800.toArgb()
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