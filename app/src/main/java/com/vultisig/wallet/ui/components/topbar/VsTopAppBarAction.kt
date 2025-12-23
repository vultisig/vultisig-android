package com.vultisig.wallet.ui.components.topbar

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import com.vultisig.wallet.ui.components.buttons.DesignType
import com.vultisig.wallet.ui.components.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonType

@Composable
fun VsTopAppBarAction(
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
) {
    VsCircleButton(
        onClick = { onClick?.invoke() },
        size = VsCircleButtonSize.Small,
        type = VsCircleButtonType.Secondary,
        designType = DesignType.Shined,
        icon = icon,
    )
}