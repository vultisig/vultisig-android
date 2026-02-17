package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState.Error
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.performHaptic
import com.vultisig.wallet.ui.utils.showReviewPopUp

@Composable
internal fun KeysignScreen(
    txType: Route.Keysign.Keysign.TxType,
    viewModel: KeysignFlowViewModel = hiltViewModel(),
) {

    val keysignFlowState by viewModel.currentState.collectAsState()

    when (val state = keysignFlowState) {
        is KeysignFlowState.PeerDiscovery -> {
            KeysignPeerDiscovery(
                viewModel = viewModel,
                txType = txType,
            )
        }

        is KeysignFlowState.Keysign -> {
            Keysign(
                viewModel = viewModel.keysignViewModel,
                onError = { viewModel.moveToState(Error(it)) },
                onComplete = viewModel::complete,
            )
        }

        is Error -> {
            KeysignErrorScreen(
                errorMessage = state.errorMessage,
                tryAgain = viewModel::tryAgain,
            )
        }
    }
}

@Composable
private fun Keysign(
    viewModel: KeysignViewModel,
    onError: (String) -> Unit,
    onComplete: () -> Unit,
    onKeysignFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }
    val view = LocalView.current

    val wrapperViewModel = hiltViewModel(
        creationCallback = { factory: KeySignWrapperViewModel.Factory ->
            factory.create(viewModel)
        }
    )

    val keysignViewModel = wrapperViewModel.viewModel

    val state: KeysignState = keysignViewModel.currentState.collectAsState().value
    LaunchedEffect(state) {
        when (state) {

            is KeysignState.Error -> onError(state.errorMessage)
            is KeysignState.KeysignECDSA,
            is KeysignState.KeysignEdDSA -> {
                view.performHaptic()
            }

            is KeysignState.KeysignFinished -> {
                view.performHaptic()
                onKeysignFinished?.invoke()
                reviewManager.showReviewPopUp(context)
            }
            else -> Unit
        }
    }
    KeysignView(
        state = state,
        transactionTypeUiModel = keysignViewModel.transactionTypeUiModel,
        txHash = keysignViewModel.txHash.collectAsState().value,
        approveTransactionHash = keysignViewModel.approveTxHash.collectAsState().value,
        transactionLink = keysignViewModel.txLink.collectAsState().value,
        approveTransactionLink = keysignViewModel.approveTxLink.collectAsState().value,
        onComplete = onComplete,
        progressLink = keysignViewModel.swapProgressLink.collectAsState().value,
        showToolbar = true,
        onBack = viewModel::navigateToHome,
        onAddToAddressBook = keysignViewModel::navigateToAddressBook,
        showSaveToAddressBook = keysignViewModel.showSaveToAddressBook.collectAsState().value,
    )
}