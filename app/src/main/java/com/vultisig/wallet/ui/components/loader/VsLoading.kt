package com.vultisig.wallet.ui.components.loader

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.theme.Theme

@Preview
@Composable
internal fun V2Loading(
    modifier: Modifier = Modifier,
){
    CircularProgressIndicator(
        color = Theme.colors.alerts.success,
        trackColor = Theme.colors.border.normal,
        modifier = modifier
    )
}