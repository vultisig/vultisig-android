package com.vultisig.wallet.ui.components.scaffold

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.buttons.DesignType
import com.vultisig.wallet.ui.components.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.topbar.VsTopbar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    applyDefaultPaddings: Boolean = true,
    applyScaffoldPaddings: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {

    VsScaffold(
        modifier = modifier,
        content = content,
        bottomBar = bottomBar,
        applyDefaultPaddings = applyDefaultPaddings,
        applyScaffoldPaddings = applyScaffoldPaddings,
        topBar = {
            VsTopbar(
                title = title,
                onBackClick = onBackClick,
            )
        }
    )
}

@Composable
internal fun VsScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    applyDefaultPaddings: Boolean = true,
    applyScaffoldPaddings: Boolean = true,
    onBackClick: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    VsScaffold(
        modifier = modifier,
        content = content,
        bottomBar = bottomBar,
        applyDefaultPaddings = applyDefaultPaddings,
        applyScaffoldPaddings = applyScaffoldPaddings,
        topBar = {
            VsTopbar(
                title = title,
                onBackClick = onBackClick,
                actions = actions,
            )
        }
    )
}

@Composable
internal fun VsScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    applyDefaultPaddings: Boolean = true,
    applyScaffoldPaddings: Boolean = true,
    onBackClick: (() -> Unit)?,
    @DrawableRes rightIcon: Int?,
    onRightIconClick: (() -> Unit)?,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    VsScaffold(
        modifier = modifier,
        content = content,
        bottomBar = bottomBar,
        applyDefaultPaddings = applyDefaultPaddings,
        applyScaffoldPaddings = applyScaffoldPaddings,
        topBar = {
            VsTopbar(
                title = title,
                onBackClick = onBackClick,
                actions = rightIcon?.let {
                    {
                        V2TopbarButton(
                            icon = rightIcon,
                            onClick = onRightIconClick ?: {}
                        )
                    }
                } ?: {},
            )
        }
    )
}

@Composable
internal fun V2TopbarButton(
    icon: Int,
    onClick: () -> Unit
) {
    VsCircleButton(
        icon = icon,
        onClick = onClick,
        type = VsCircleButtonType.Secondary,
        designType = DesignType.Shined,
        size = VsCircleButtonSize.Small,
        hasBorder = false,
    )
}

@Composable
internal fun VsScaffold(
    modifier: Modifier = Modifier,
    applyDefaultPaddings: Boolean,
    applyScaffoldPaddings: Boolean,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        modifier = modifier,
        containerColor = V2Scaffold.CONTAINER_COLOR,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (applyScaffoldPaddings) {
                        Modifier.padding(it)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (applyDefaultPaddings) {
                        Modifier
                            .padding(
                                horizontal = V2Scaffold.PADDING_HORIZONTAL,
                                vertical = V2Scaffold.PADDING_VERTICAL,
                            )
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}

internal object V2Scaffold {
    internal val PADDING_VERTICAL = 12.dp
    internal val PADDING_HORIZONTAL = 16.dp
    internal val CONTAINER_COLOR: Color
        @Composable
        get() = Theme.colors.backgrounds.primary

}

