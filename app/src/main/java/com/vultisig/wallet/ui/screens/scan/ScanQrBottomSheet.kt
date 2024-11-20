package com.vultisig.wallet.ui.screens.scan

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrBottomSheet (
    onDismiss: () -> Unit,
    onScanSuccess: (qr: String) -> Unit,
) {

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        dragHandle = null,
        containerColor = Theme.colors.oxfordBlue800,
        onDismissRequest = onDismiss,
    ) {
        ScanQrScreen(
            onScanSuccess = onScanSuccess,
            onDismiss = onDismiss,
        )
    }
}