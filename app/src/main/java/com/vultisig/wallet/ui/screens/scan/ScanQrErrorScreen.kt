package com.vultisig.wallet.ui.screens.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.ErrorView
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ScanQrErrorScreen(
    viewModel: ScanQrErrorViewModel = hiltViewModel(),
) {
    ScanQrErrorScreen(viewModel::enableChain)
}

@Composable
private fun ScanQrErrorScreen(onEnableChainClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
    ) {

        Spacer(modifier = Modifier.height(5.dp))

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            centerText = stringResource(R.string.scan_qr_default_title),
            navController = rememberNavController()
        )
        ErrorView(
            errorLabel = stringResource(R.string.enable_chain_warning),
            buttonText = stringResource(R.string.enable_chain_button),
            onButtonClick = onEnableChainClick,
        )
    }
}


@Preview
@Composable
fun ScanQrErrorScreenPreview() {
    ScanQrErrorScreen {}
}