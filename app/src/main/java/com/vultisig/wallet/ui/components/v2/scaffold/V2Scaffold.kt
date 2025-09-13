package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
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
            VsTopAppBar(
                title = title,
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
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
            VsTopAppBar(
                title = title,
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                actions = actions,
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