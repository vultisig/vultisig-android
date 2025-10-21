package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun PasteIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    onPaste: (String) -> Unit,
) {
    val clipboardData = VsClipboardService.getClipboardData()
    UiIcon(
        modifier = modifier,
        drawableResId = R.drawable.paste,
        size = size,
        onClick = {
            val value = clipboardData.value
            if (value.isNullOrEmpty()) return@UiIcon
            onPaste(value)
        }
    )
}
