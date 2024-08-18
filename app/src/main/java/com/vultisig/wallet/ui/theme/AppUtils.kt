package com.vultisig.wallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

@Composable
internal fun AppUtils(
    appColor: Colors,
    menloTypography: VultisigTypography,
    montserratTypography: VultisigTypography,
    content: @Composable () -> Unit,
) {

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
        LocalAppColors provides appColor,
        LocalMenloFamilyTypography provides menloFamilyTypography,
        LocalMontserratFamilyTypography provides montserratFamilyTypography
    ) {
        content()
    }

}

internal val LocalAppColors = compositionLocalOf {
    Colors.Default
}
internal val LocalMenloFamilyTypography = compositionLocalOf {
    menloTypography
}
internal val LocalMontserratFamilyTypography = compositionLocalOf {
    montserratTypography
}