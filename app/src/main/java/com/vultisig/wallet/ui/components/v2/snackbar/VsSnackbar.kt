package com.vultisig.wallet.ui.components.v2.snackbar

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.loading.V2ProgressiveLoading
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsSnackBar(modifier: Modifier = Modifier, snackbarState: VSSnackbarState) {
    val snackbarState by snackbarState.progressState.collectAsState()
    if (snackbarState.isVisible.not()) return
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        (LocalView.current.parent as DialogWindowProvider).window.apply {
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        VsSnackBarContent(modifier, snackbarState)
    }
}

@Composable
private fun VsSnackBarContent(modifier: Modifier, snackbarState: ProgressState) {
    V2Container(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        type = ContainerType.TERTIARY,
        borderType = ContainerBorderType.Bordered(color = Theme.v2.colors.border.normal),
        cornerType = CornerType.RoundedCornerShape(size = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val (color, icon, iconSize) =
                when (snackbarState.type) {
                    SnackbarType.Success ->
                        Triple(Theme.v2.colors.alerts.success, R.drawable.ic_check, 20.dp)

                    SnackbarType.Warning ->
                        Triple(Theme.v2.colors.alerts.warning, R.drawable.ic_warning, 10.dp)

                    SnackbarType.Error ->
                        Triple(Theme.v2.colors.alerts.error, R.drawable.error_critical, 10.dp)
                }
            V2ProgressiveLoading(
                progress = snackbarState.progress,
                progressColor = color,
                iconColor = color,
                icon = icon,
                iconSize = iconSize,
            )

            UiSpacer(size = 8.dp)
            Text(
                text = snackbarState.message,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.neutrals.n50,
            )
        }
    }
}

@Preview
@Composable
private fun VsSnackBarSuccessContentPreview() {
    VsSnackBarContent(
        modifier = Modifier,
        snackbarState =
            ProgressState(
                message = "This is a success snackbar",
                isVisible = true,
                type = SnackbarType.Success,
                progress = 0.5f,
            ),
    )
}

@Preview
@Composable
private fun VsSnackBarErrorContentPreview() {
    VsSnackBarContent(
        modifier = Modifier,
        snackbarState =
            ProgressState(
                message = "This is an error snackbar",
                isVisible = true,
                type = SnackbarType.Error,
                progress = 0.5f,
            ),
    )
}

@Preview
@Composable
private fun VsSnackBarWarningContentPreview() {
    VsSnackBarContent(
        modifier = Modifier,
        snackbarState =
            ProgressState(
                message = "This is a warning snackbar",
                isVisible = true,
                type = SnackbarType.Warning,
                progress = 0.5f,
            ),
    )
}
