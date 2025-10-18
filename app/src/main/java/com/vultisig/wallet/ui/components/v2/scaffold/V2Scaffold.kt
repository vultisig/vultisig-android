package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun V2Scaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit
) {

    V2Scaffold(
        modifier = modifier,
        content = content,
        topBar = {
            V2Topbar(
                title = title,
                onBackClick = onBackClick,
            )
        }
    )
}

@Composable
internal fun V2Scaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit
) {
    V2Scaffold(
        modifier = modifier,
        content = content,
        topBar = {
            V2Topbar(
                title = title,
                onBackClick = onBackClick,
                actions = actions,
            )
        }
    )
}

@Composable
internal fun V2Scaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit,
    @DrawableRes rightIcon: Int,
    onRightIconClick: () -> Unit,
    content: @Composable () -> Unit
) {
    V2Scaffold(
        modifier = modifier,
        content = content,
        topBar = {
            V2Topbar(
                title = title,
                onBackClick = onBackClick,
                actions = {
                    VsCircleButton(
                        icon = rightIcon,
                        onClick = onRightIconClick,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined,
                        size = VsCircleButtonSize.Small,
                        hasBorder = false,
                    )
                },
            )
        }
    )
}

@Composable
private fun V2Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = topBar,
        modifier = modifier,
        containerColor = Theme.colors.backgrounds.primary,
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            content()
        }
    }
}