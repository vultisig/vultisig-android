package com.vultisig.wallet.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiPlaceholderLoader(
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Theme.colors.backgrounds.tertiary_2)
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp),
    )
}

@Preview
@Composable
private fun UiPlaceholderLoaderPreview() {
    UiPlaceholderLoader()
}