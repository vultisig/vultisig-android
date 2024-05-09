package com.vultisig.wallet.app.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.theme.CompactDimens
import com.vultisig.wallet.ui.theme.CompactMediumDimens
import com.vultisig.wallet.ui.theme.CompactSmallDimens
import com.vultisig.wallet.ui.theme.ExpandedDimens
import com.vultisig.wallet.ui.theme.OnDarkCustomColorsPalette
import com.vultisig.wallet.ui.theme.OnLightCustomColorsPalette
import com.vultisig.wallet.ui.theme.Shapes

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun OnBoardingComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    activity: Activity = LocalContext.current as MainActivity,
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) {
        OnDarkCustomColorsPalette
    } else {
        OnLightCustomColorsPalette
    }
    val window = calculateWindowSizeClass(activity = activity)
    val config = LocalConfiguration.current
    var appDimens = CompactDimens
    when (window.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            if (config.screenWidthDp <= 360) {
                appDimens = CompactSmallDimens
            } else if (config.screenWidthDp <= 599) {
                appDimens = CompactMediumDimens
            } else {
                appDimens = CompactDimens
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
    if (Build.VERSION.SDK_INT >= 21) {
        val window = (LocalContext.current as Activity).window
        if (darkTheme) {
            window.statusBarColor = appColors.oxfordBlue800.toArgb()
        } else {
//            window.statusBarColor = appColors.neutral0.toArgb()
            window.statusBarColor = appColors.oxfordBlue800.toArgb()
        }
    }

}

// create an extension value for LocalAppDimens.current
//for example you want get data of MaterialTheme.dimens
//you have to call MaterialTheme.LocalAppDimens.current.dimens
//to get dimens
//instead of that you can use extension method to get data in easy way
val MaterialTheme.dimens
    @Composable
    get() = LocalAppDimens.current

val MaterialTheme.appColor
    @Composable
    get() = LocalAppColors.current

internal val MaterialTheme.menloFamily
    @Composable
    get() = LocalMenloFamilyTypography.current

internal val MaterialTheme.montserratFamily
    @Composable
    get() = LocalMontserratFamilyTypography.current