package com.vultisig.wallet.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        color = Theme.colors.oxfordBlue400,
        modifier = modifier,
    )
}