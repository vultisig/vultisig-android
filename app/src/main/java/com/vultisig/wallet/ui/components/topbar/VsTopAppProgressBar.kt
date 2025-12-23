package com.vultisig.wallet.ui.components.topbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.DashedProgressIndicator
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsTopAppProgressBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigationContent: @Composable () -> Unit = {},
    progress: Int = 3,
    total: Int = 12,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                if (title != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        text = title,
                        style = Theme.brockmann.headings.title3,
                        color = Theme.colors.text.primary,
                        textAlign = TextAlign.Start,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Theme.colors.backgrounds.primary,
            ),
            navigationIcon = {
                navigationContent()
            },
            actions = actions,
            modifier = modifier.padding(end = 16.dp),
        )
        DashedProgressIndicator(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            progress = progress,
            totalNumberOfBars = total,
        )
    }
}