package com.vultisig.wallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.vultisig.wallet.ui.theme.Theme.colors

@Composable
internal fun AppUtils(
    appColor: Colors,
    menloTypography: VultisigTypography,
    montserratTypography: VultisigTypography,
    brockmannTypography: VsTypography,
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
    val brockmannFamilyTypography = remember {
        brockmannTypography
    }
    CompositionLocalProvider(
        LocalAppColors provides appColor,
        LocalMenloFamilyTypography provides menloFamilyTypography,
        LocalMontserratFamilyTypography provides montserratFamilyTypography,
        LocalBrockmannFamilyTypography provides brockmannFamilyTypography,
    ) {
        content()
    }

}


internal val LocalAppColors = compositionLocalOf {
    colors
}
internal val LocalMenloFamilyTypography = compositionLocalOf {
    menloTypography
}
internal val LocalMontserratFamilyTypography = compositionLocalOf {
    montserratTypography
}
internal val LocalBrockmannFamilyTypography = compositionLocalOf {
    brockmannTypography
}