package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState.Error
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.performHaptic

@Composable
internal fun KeysignScreen(
    txType: Route.Keysign.Keysign.TxType,
    viewModel: KeysignFlowViewModel = hiltViewModel(),
) {

    val keysignFlowState by viewModel.currentState.collectAsState()

    when (val state = keysignFlowState) {
        is KeysignFlowState.PeerDiscovery -> {
            KeysignPeerDiscovery(viewModel = viewModel, txType = txType)
        }

        is KeysignFlowState.Keysign -> {
            val keysignVm = viewModel.keysignViewModel
            if (keysignVm != null) {
                Keysign(
                    viewModel = keysignVm,
                    onError = { viewModel.moveToState(Error(it)) },
                    onComplete = viewModel::complete,
                )
            } else {
                LaunchedEffect(viewModel) {
                    viewModel.moveToState(Error("Failed to initialize keysign".asUiText()))
                }
            }
        }

        is Error -> {
            KeysignErrorScreen(
                errorMessage = state.errorMessage,
                tryAgain = viewModel::tryAgain,
                onBack = viewModel::back,
            )
        }
    }
}

@Composable
private fun Keysign(
    viewModel: KeysignViewModel,
    onError: (UiText) -> Unit,
    onComplete: () -> Unit,
) {
    val view = LocalView.current

    val wrapperViewModel =
        hiltViewModel(
            creationCallback = { factory: KeySignWrapperViewModel.Factory ->
                factory.create(viewModel)
            }
        )

    val keysignViewModel = wrapperViewModel.viewModel

    val uiState = keysignViewModel.state.collectAsState().value
    val state: KeysignState = uiState.signingState
    LaunchedEffect(state) {
        when (state) {
            is KeysignState.Error -> onError(state.errorMessage)
            is KeysignState.KeysignECDSA,
            is KeysignState.KeysignEdDSA,
            is KeysignState.KeysignFinished -> {
                view.performHaptic()
            }
            else -> Unit
        }
    }
    KeysignView(
        state = state,
        transactionTypeUiModel = uiState.transactionUiModel,
        txHash = uiState.txHash,
        approveTransactionHash = uiState.approveTxHash,
        transactionLink = uiState.txLink,
        approveTransactionLink = uiState.approveTxLink,
        onComplete = onComplete,
        progressLink = uiState.swapProgressLink,
        showToolbar = true,
        onBack = viewModel::navigateToHome,
        onAddToAddressBook = keysignViewModel::navigateToAddressBook,
        showSaveToAddressBook = uiState.showSaveToAddressBook,
        hasBackClick = true,
        dappMetadata = keysignViewModel.dappMetadata,
        coinLogoRes = keysignViewModel.coinLogoRes,
        operationHero = uiState.operationHero,
    )
}
