package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.rive.RiveAnimation

@Composable
fun SetupVaultRive(animationRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .widthIn(max = SETUP_VAULT_RIVE_MAX_WIDTH)
                .fillMaxWidth()
                .aspectRatio(SETUP_VAULT_RIVE_RATIO)
    ) {
        RiveAnimation(animation = animationRes)
    }
}

private val SETUP_VAULT_RIVE_MAX_WIDTH = 350.dp
private const val SETUP_VAULT_RIVE_RATIO = 350f / 240f

@Preview
@Composable
fun SetupVaultRivePreview() {
    SetupVaultRive(animationRes = R.raw.riv_keygen)
}
