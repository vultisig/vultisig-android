package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keysign.KeysignState
import com.vultisig.wallet.presenter.keysign.KeysignViewModel
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Keysign(
    viewModel: KeysignViewModel,
    onComplete: () -> Unit,
) {

    val wrapperViewModel = hiltViewModel(
        creationCallback = { factory: KeySignWrapperViewModel.Factory ->
            factory.create(viewModel)
        }
    )

    val keysignViewModel = wrapperViewModel.viewModel

    KeysignScreen(
        state = keysignViewModel.currentState.collectAsState().value,
        errorMessage = keysignViewModel.errorMessage.value,
        txHash = keysignViewModel.txHash.collectAsState().value,
        transactionLink = keysignViewModel.txLink.collectAsState().value,
        onComplete = onComplete,
    )
}

@Composable
internal fun KeysignScreen(
    state: KeysignState,
    txHash: String,
    transactionLink: String,
    errorMessage: String,
    onComplete: () -> Unit,
) {
    KeepScreenOn()
    val text = when (state) {
        KeysignState.CreatingInstance -> stringResource(id = R.string.keysign_screen_preparing_vault)
        KeysignState.KeysignECDSA -> stringResource(id = R.string.keysign_screen_signing_with_ecdsa)
        KeysignState.KeysignEdDSA -> stringResource(id = R.string.vault_detail_screen_eddsa)
        KeysignState.KeysignFinished -> stringResource(id = R.string.keysign_screen_keysign_finished)
        KeysignState.ERROR -> stringResource(
            id = R.string.keysign_screen_error_please_try_again,
            errorMessage
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state == KeysignState.KeysignFinished) {
            TransactionDoneView(
                transactionHash = txHash,
                transactionLink = transactionLink,
                onComplete = onComplete,
            )
        } else {
            UiSpacer(weight = 1f)
            Text(
                text = text,
                color = Theme.colors.neutral0,
                style = Theme.menlo.subtitle1,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 32.dp)

            UiCirclesLoader()

            UiSpacer(weight = 1f)

            DevicesOnSameNetworkHint(
                title = stringResource(id = R.string.keysign_screen_keep_devices_on_the_same_wifi_network_with_vultisig_open)
            )

            UiSpacer(size = 80.dp)
        }
    }
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignScreen(
        state = KeysignState.CreatingInstance,
        errorMessage = "Error",
        txHash = "0x1234567890",
        transactionLink = "",
        onComplete = {},
    )
}
