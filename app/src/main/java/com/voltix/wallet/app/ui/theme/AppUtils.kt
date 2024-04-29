package com.voltix.wallet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.voltix.wallet.ui.theme.ColorsPalette
import com.voltix.wallet.ui.theme.CompactDimens
import com.voltix.wallet.ui.theme.Dimens
import com.voltix.wallet.ui.theme.OnLightCustomColorsPalette

@Composable
fun AppUtils(
    appDimens: Dimens,
    appColor: ColorsPalette,
    menloFamilyTypography: Typography,
    montserratFamilyTypography: Typography,
    content: @Composable () -> Unit,
) {
    val appDimens = remember {
        appDimens
    }
    val appColor = remember {
        appColor
    }
    val menloFamilyTypography = remember {
        menloFamilyTypography
    }
    val montserratFamilyTypography = remember {
        montserratFamilyTypography
    }
    CompositionLocalProvider(
        LocalAppDimens provides appDimens,
        LocalAppColors provides appColor,
        LocalMenloFamilyTypography provides menloFamilyTypography,
        LocalMontserratFamilyTypography provides montserratFamilyTypography
    ) {
        content()
    }

}

val LocalAppDimens = compositionLocalOf {
    CompactDimens
}
val LocalAppColors = compositionLocalOf {
    OnLightCustomColorsPalette
}
val LocalMenloFamilyTypography = compositionLocalOf {
    compactMenloFamilyTypography
}
val LocalMontserratFamilyTypography = compositionLocalOf {
    compactMontserratFamilyTypography
}