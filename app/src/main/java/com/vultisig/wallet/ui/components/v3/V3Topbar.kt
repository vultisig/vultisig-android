package com.vultisig.wallet.ui.components.v3

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3Topbar(
    title: String?,
    onBackClick: (() -> Unit)?,
    transparentBackground: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        modifier = Modifier,
        title = {
            Text(
                text = title.orEmpty(),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )
        },
        navigationIcon = onBackClick?.let {
            {
                Row {
                    UiSpacer(
                        size = 12.dp
                    )
                    VsCircleButton(
                        onClick = onBackClick,
                        size = VsCircleButtonSize.Small,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined,
                        icon = R.drawable.ic_caret_left,
                    )
                }
            }
        } ?: {},
        actions = {
            actions?.let {
                it()
                UiSpacer(
                    size = 12.dp
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (transparentBackground)
                    Color.Transparent
                else Theme.v2.colors.backgrounds.background,
        ),
        windowInsets = WindowInsets(0.dp)
    )
}

@Preview
@Composable
private fun PreviewV3Topbar() {
    V3Topbar(
        title = "Title",
        onBackClick = null
    )
}
@Preview
@Composable
private fun PreviewV3Topbar2() {
    V3Topbar(
        title = "Title",
        onBackClick = {},
    )
}

@Preview
@Composable
private fun PreviewV3Topbar3() {
    V3Topbar(
        title = "Title",
        onBackClick = {},
        actions = {
            VsCircleButton(
                onClick = {},
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                icon = R.drawable.camera,
            )
        }
    )
}

@Preview
@Composable
private fun PreviewV3Topbar4() {
    V3Topbar(
        title = null,
        onBackClick = null,
        actions = {
            VsCircleButton(
                onClick = {},
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                icon = R.drawable.camera,
            )
        }
    )
}

@Preview
@Composable
private fun PreviewV3Topbar5() {
    V3Topbar(
        title = "title",
        onBackClick = null,
        actions = {
            VsCircleButton(
                onClick = {},
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                icon = R.drawable.camera,
            )
        }
    )
}

@Preview
@Composable
private fun PreviewV3Topbar6() {
    V3Topbar(
        title = "title",
        onBackClick = null,
        actions = null
    )
}

@Preview
@Composable
private fun PreviewV3Topbar7() {
    V3Topbar(
        title = "title",
        onBackClick = null,
        actions = { }
    )
}