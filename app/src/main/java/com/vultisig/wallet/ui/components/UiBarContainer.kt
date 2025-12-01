package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiBarContainer(
    navController: NavController,
    title: String,
    showStartIcon: Boolean = true,
    @DrawableRes endIcon: Int? = null,
    onEndIconClick: () -> Unit = {},
    onStartIconClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val startIcon = if (showStartIcon) R.drawable.ic_caret_left else null
    Column(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = title,
            startIcon = startIcon,
            endIcon = endIcon,
            onEndIconClick = onEndIconClick,
            onStartIconClick = onStartIconClick,
            navController = navController
        )

        content()
    }
}