package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.keysign.JoinKeysignViewModel
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.screens.VerifyTransactionScreen

@Composable
internal fun VerifyKeysignScreen(
    navController: NavHostController,
    viewModel: JoinKeysignViewModel,
) {
    VerifyTransactionScreen(
        navController = navController,
        state = VerifyTransactionUiModel(
            transaction = TransactionUiModel(
                srcAddress = viewModel.keysignPayload?.coin?.address ?: "",
                dstAddress = viewModel.keysignPayload?.toAddress ?: "",
                tokenValue = viewModel.keysignPayload?.toAmount.toString(),

                // TODO fetch these values
                fiatValue = "",
                fiatCurrency = "",
                gasValue = ""
            )
        ),
        isConsentsEnabled = false,
        confirmTitle = stringResource(R.string.verify_transaction_join_keysign),
        onConfirm = viewModel::joinKeysign,
    )
}