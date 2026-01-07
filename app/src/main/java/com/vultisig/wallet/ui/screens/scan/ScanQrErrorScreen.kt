package com.vultisig.wallet.ui.screens.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorView

@Composable
internal fun ScanQrErrorScreen(
    viewModel: ScanQrErrorViewModel = hiltViewModel(),
) {
    ScanQrErrorScreen(viewModel::back)
}

@Composable
private fun ScanQrErrorScreen(back: () -> Unit) {

        ErrorView(
            title = stringResource(R.string.scan_qr_code_error_text),
            buttonText = stringResource(R.string.scan_qr_code_error_button),
            onButtonClick = back,
        )
}


@Preview
@Composable
fun ScanQrErrorScreenPreview() {
    ScanQrErrorScreen {}
}