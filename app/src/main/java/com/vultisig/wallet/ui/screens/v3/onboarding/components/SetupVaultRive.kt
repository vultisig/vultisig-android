package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun SetupVaultRive(
    animationRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 350.dp, height = 240.dp)
            .background(
                color = Theme.v2.colors.backgrounds.secondary
            )
            .dashedBorder(
                width = 1.dp,
                color = Theme.v2.colors.border.light,
                cornerRadius = 0.dp,
                intervalLength = 4.dp,
                dashLength = 4.dp
            )
    ) {
        RiveAnimation(
            animation = animationRes
        )
    }
}

@Preview
@Composable
fun SetupVaultRivePreview(){
    SetupVaultRive(
        animationRes = R.raw.riv_keygen
    )
}