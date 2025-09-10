package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize

@Composable
internal fun CameraButton(
    onClick: () -> Unit
) {
    VsCircleButton(
        onClick = onClick,
        icon = R.drawable.camera,
        size = VsCircleButtonSize.Medium
    )
}

@Preview
@Composable
internal fun PreviewCameraButton() {
    CameraButton(
        onClick = {}
    )
}