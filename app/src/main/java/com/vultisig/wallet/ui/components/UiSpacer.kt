package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun UiSpacer(
    size: Dp,
) {
    Spacer(modifier = Modifier.size(size))
}

@Composable
internal fun ColumnScope.UiSpacer(
    weight: Float,
) {
    Spacer(modifier = Modifier.weight(weight))
}

@Composable
internal fun RowScope.UiSpacer(
    weight: Float,
) {
    Spacer(modifier = Modifier.weight(weight))
}