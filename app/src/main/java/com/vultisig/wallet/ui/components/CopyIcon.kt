package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun CopyIcon(
    modifier: Modifier = Modifier,
    textToCopy: String,
    size: Dp = 20.dp,
    onCopyCompleted: (String) -> Unit = {},
    tint: Color? = null,
) {
    val context = LocalContext.current
    UiIcon(
        modifier = modifier,
        drawableResId = R.drawable.copy,
        size = size,
        onClick = {
            VsClipboardService.copy(context, textToCopy)
            onCopyCompleted(textToCopy)
        },
        tint = tint ?: Theme.colors.neutral100,
    )
}

