package com.vultisig.wallet.ui.screens.scan

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import com.vultisig.wallet.ui.models.ScanQrUiModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrBottomSheet (
    uiModel: ScanQrUiModel,
    onDismiss: () -> Unit,
    onScanSuccess: (qr: String) -> Unit,
    onError: (String) -> Unit = {},
) {

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        dragHandle = null,
        containerColor = Theme.v2.colors.backgrounds.transparent,
        onDismissRequest = onDismiss,
        shape = RectangleShape
    ) {
        ScanQrScreen(
            uiModel = uiModel,
            onScanSuccess = onScanSuccess,
            onDismiss = onDismiss,
            onError = onError,
        )
    }
}