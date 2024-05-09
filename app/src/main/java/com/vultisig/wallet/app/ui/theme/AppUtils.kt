package com.vultisig.wallet.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.vultisig.wallet.ui.theme.ColorsPalette
import com.vultisig.wallet.ui.theme.CompactDimens
import com.vultisig.wallet.ui.theme.Dimens
import com.vultisig.wallet.ui.theme.OnLightCustomColorsPalette

@Composable
internal fun AppUtils(
    appDimens: Dimens,
    appColor: ColorsPalette,
    menloTypography: VultisigTypography,
    montserratTypography: VultisigTypography,
    content: @Composable () -> Unit,
) {
    val appDimens = remember {
        appDimens
    }
    val appColor = remember {
        appColor
    }
    val menloFamilyTypography = remember {
        menloTypography
    }
    val montserratFamilyTypography = remember {
        montserratTypography
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
internal val LocalMenloFamilyTypography = compositionLocalOf {
    menloTypography
}
internal val LocalMontserratFamilyTypography = compositionLocalOf {
    montserratTypography
}