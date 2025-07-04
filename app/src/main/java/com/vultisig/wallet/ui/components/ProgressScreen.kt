package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
internal fun ProgressScreen(
    navController: NavController,
    title: String,
    showStartIcon: Boolean = true,
    @DrawableRes endIcon: Int? = null,
    onEndIconClick: () -> Unit = {},
    onStartIconClick: (() -> Unit)? = null,
    progress: Float,
    content: @Composable BoxScope.() -> Unit
) {
    UiBarContainer(
        navController = navController,
        showStartIcon = showStartIcon,
        endIcon = endIcon,
        onEndIconClick = onEndIconClick,
        onStartIconClick = onStartIconClick,
        title = title
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            UiSpacer(size = 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content,
            )
        }
    }
}

@Preview
@Composable
private fun ProgressScreenPreview() {
    ProgressScreen(
        navController = rememberNavController(),
        title = "Title",
        progress = 0.5f,
        content = { }
    )
}