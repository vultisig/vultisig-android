package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    @DrawableRes endIcon: Int? = null,
    onEndIconClick: () -> Unit = {},
    progress: Float,
    content: @Composable BoxScope.() -> Unit
) {
    UiBarContainer(
        navController = navController,
        endIcon = endIcon,
        onEndIconClick = onEndIconClick,
        title = title
    ) {
        UiScrollableColumn(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            UiSpacer(size = 16.dp)

            UiLinearProgressIndicator(
                progress = progress,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            UiSpacer(size = 32.dp)

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