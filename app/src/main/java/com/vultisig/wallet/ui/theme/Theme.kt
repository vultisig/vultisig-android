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
    activity: Activity = LocalContext.current as MainActivity,
    content: @Composable () -> Unit,
) {
    val appColors = Colors.Default
    val window = calculateWindowSizeClass(activity = activity)
    val config = LocalConfiguration.current
    val appDimens: Dimens
    when (window.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            appDimens = if (config.screenWidthDp <= 360) {
                CompactSmallDimens
            } else if (config.screenWidthDp <= 599) {
                CompactMediumDimens
            } else {
                CompactDimens
            }
        }

        else -> {
            appDimens = ExpandedDimens
        }

    }

    AppUtils(
        appDimens = appDimens,
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

// create an extension value for LocalAppDimens.current
//for example you want get data of MaterialTheme.dimens
//you have to call MaterialTheme.LocalAppDimens.current.dimens
//to get dimens
//instead of that you can use extension method to get data in easy way
@Deprecated("Just specify the value directly", ReplaceWith("X.dp"))
val MaterialTheme.dimens
    @Composable
    get() = LocalAppDimens.current

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
    get() = SolidColor(Theme.colors.neutral100)