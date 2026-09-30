package com.vultisig.wallet.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import kotlinx.coroutines.delay

/** How long a copy control shows its checkmark before reverting. */
internal const val COPIED_CHECK_DURATION_MS = 1_500L

/**
 * A copy button that confirms in place: the icon turns into a checkmark for a moment after each
 * copy, on every Android version. Callers add a message of their own only when
 * [VsClipboardService.needsCopyConfirmation] says the system won't.
 */
@Composable
internal fun CopyIcon(
    modifier: Modifier = Modifier,
    textToCopy: String,
    size: Dp = 20.dp,
    onCopyCompleted: (String) -> Unit = {},
    tint: Color? = null,
) {
    val context = LocalContext.current
    var copyCount by remember { mutableIntStateOf(0) }
    var isShowingCheck by remember { mutableStateOf(false) }

    LaunchedEffect(copyCount) {
        if (copyCount == 0) return@LaunchedEffect
        isShowingCheck = true
        delay(COPIED_CHECK_DURATION_MS)
        isShowingCheck = false
    }

    Crossfade(targetState = isShowingCheck, modifier = modifier, label = "copy-icon") { copied ->
        if (copied) {
            UiIcon(
                drawableResId = R.drawable.check,
                size = size,
                tint = Theme.v2.colors.alerts.success,
            )
        } else {
            UiIcon(
                drawableResId = R.drawable.copy,
                size = size,
                onClick = {
                    VsClipboardService.copy(context, textToCopy)
                    copyCount++
                    onCopyCompleted(textToCopy)
                },
                tint = tint ?: Theme.v2.colors.neutrals.n100,
            )
        }
    }
}
