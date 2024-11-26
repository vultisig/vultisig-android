package com.vultisig.wallet.ui.screens.scan

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        containerColor = Theme.colors.transparent,
        onDismissRequest = onDismiss,
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        ScanQrScreen(
            onScanSuccess = onScanSuccess,
            roundedCorners = true,
            onDismiss = onDismiss,
        )
    }
}