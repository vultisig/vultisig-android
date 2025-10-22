package com.vultisig.wallet.ui.components.v2.topbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
internal fun V2Topbar(
    title: String?,
    onBackClick: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        modifier = Modifier,
        title = {
            Text(
                text = title.orEmpty(),
                style = Theme.brockmann.headings.title3,
                color = Theme.colors.text.primary,
            )
        },
        navigationIcon = onBackClick?.let {
            {
                VsCircleButton(
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                    onClick = onBackClick,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Secondary,
                    designType = DesignType.Shined,
                    icon = R.drawable.ic_caret_left,
                )
            }
        } ?: {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Theme.colors.backgrounds.primary,
        ),
        windowInsets = WindowInsets(0.dp)
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2Topbar(
    title: String?,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = Modifier,
        title = {
            Text(
                text = title.orEmpty(),
                style = Theme.brockmann.headings.title3,
                color = Theme.colors.text.primary,
            )
        },
        navigationIcon = {
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
        },
        actions = {
            actions()
            UiSpacer(
                size = 12.dp
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Theme.colors.backgrounds.primary,
        ),
        windowInsets = WindowInsets(0.dp)
    )
}

@Preview
@Composable
private fun PreviewV2Topbar() {
    V2Topbar(
        title = "Title",
    )
}
@Preview
@Composable
private fun PreviewV2Topbar2() {
    V2Topbar(
        title = "Title",
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