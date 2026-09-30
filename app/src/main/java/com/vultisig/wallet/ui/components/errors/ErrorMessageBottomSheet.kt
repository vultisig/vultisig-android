package com.vultisig.wallet.ui.components.errors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.COPIED_CHECK_DURATION_MS
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsClipboardService
import kotlinx.coroutines.delay

/**
 * Full-screen sheet showing the raw technical error behind a friendly [ErrorView]. The trace is
 * copyable ([VsClipboardService]) and reportable — "Report Bug" copies the trace and opens the
 * Vultisig Discord support channel so the user can paste it.
 */
@Composable
internal fun ErrorMessageBottomSheet(rawError: String, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var copyCount by remember { mutableIntStateOf(0) }
    var isShowingCopied by remember { mutableStateOf(false) }

    LaunchedEffect(copyCount) {
        if (copyCount == 0) return@LaunchedEffect
        isShowingCopied = true
        delay(COPIED_CHECK_DURATION_MS)
        isShowingCopied = false
    }

    VsModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.error_message_sheet_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )

            Text(
                text = rawError,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(
                            color = Theme.v2.colors.backgrounds.disabled,
                            shape = Theme.v2.radius.xl,
                        )
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = Theme.v2.radius.xl,
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VsButton(
                    label = stringResource(R.string.error_message_copy),
                    iconLeft = if (isShowingCopied) R.drawable.check else R.drawable.ic_copy,
                    variant = VsButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        VsClipboardService.copy(context, rawError)
                        copyCount++
                    },
                )
                VsButton(
                    label = stringResource(R.string.error_message_report_bug),
                    variant = VsButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        VsClipboardService.copy(context, rawError)
                        uriHandler.openUri(VsAuxiliaryLinks.DISCORD)
                    },
                )
            }
        }
    }
}
