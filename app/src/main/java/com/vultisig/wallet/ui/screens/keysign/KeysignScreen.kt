package com.vultisig.wallet.ui.screens.keysign

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Deposit
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Send
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Sign
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Swap
import com.vultisig.wallet.ui.utils.showReviewPopUp

@Composable
internal fun KeysignScreen(
    transactionId: TransactionId,
    txType: Route.Keysign.Keysign.TxType,
    keysignShareViewModel: KeysignShareViewModel =
        hiltViewModel(LocalActivity.current as MainActivity)
) {
    when (txType) {
        Send -> keysignShareViewModel.loadTransaction(transactionId)
        Swap -> keysignShareViewModel.loadSwapTransaction(transactionId)
        Deposit -> keysignShareViewModel.loadDepositTransaction(transactionId)
        Sign -> keysignShareViewModel.loadSignMessageTx(transactionId)
    }

    KeysignScreen()
}

@Composable
private fun KeysignScreen(
    viewModel: KeysignFlowViewModel = hiltViewModel(),
    sharedViewModel: KeysignShareViewModel =
        hiltViewModel(LocalActivity.current as MainActivity),
) {
    val keysignFlowState by viewModel.currentState.collectAsState()

    if (!sharedViewModel.hasAllData) {
        // information is not available, go back
        viewModel.moveToState(KeysignFlowState.Error("Keysign information not available"))
    }

    when (val state = keysignFlowState) {
        is KeysignFlowState.PeerDiscovery -> {
            KeysignPeerDiscovery(
                viewModel = viewModel,
            )
        }

        is KeysignFlowState.Keysign -> {
            Keysign(
                viewModel = viewModel.keysignViewModel,
                onError = { viewModel.moveToState(KeysignFlowState.Error(it)) },
                onComplete = viewModel::complete,
            )
        }

        is KeysignFlowState.Error -> {
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
            is KeysignState.KeysignFinished -> {
                onKeysignFinished?.invoke()
                reviewManager.showReviewPopUp(context)
                wrapperViewModel.loadTransaction()
            }
            else -> Unit
        }
    }
    KeysignView(
        state = state,
        transactionTypeUiModel = wrapperViewModel.transactionUiModel.collectAsState().value,
        txHash = keysignViewModel.txHash.collectAsState().value,
        approveTransactionHash = keysignViewModel.approveTxHash.collectAsState().value,
        transactionLink = keysignViewModel.txLink.collectAsState().value,
        approveTransactionLink = keysignViewModel.approveTxLink.collectAsState().value,
        onComplete = onComplete,
        progressLink = keysignViewModel.swapProgressLink.collectAsState().value,
        showToolbar = true,
        onBack = {
            viewModel.navigateToHome()
        }
    )
}