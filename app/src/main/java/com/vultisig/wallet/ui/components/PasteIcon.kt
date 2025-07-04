package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun PasteIcon(
    modifier: Modifier = Modifier.Companion,
    size: Dp = 20.dp,
    onPaste: (String) -> Unit,
) {
    val context = LocalContext.current
    UiIcon(
        modifier = modifier,
        drawableResId = R.drawable.ic_paste,
        size = size,
        onClick = {
            val clipboardData = VsClipboardService.getClipboardData(context)
            onPaste(clipboardData)
        }
    )
}
