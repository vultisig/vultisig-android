package com.vultisig.wallet.ui.screens.deposit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositViewModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifyDepositScreen(
    viewModel: VerifyDepositViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(id = R.string.dialog_default_error_title),
            text = errorText.asString(),
            onDismiss = viewModel::dismissError,
        )
    }

    VerifyDepositScreen(
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onFastSignClick = viewModel::fastSign,
        onConfirm = viewModel::confirm,
    )
}

@Composable
internal fun VerifyDepositScreen(
    state: VerifyDepositUiModel,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    VerifyDepositScreen(
        fromAddress = state.fromAddress,
        srcTokenValue = state.srcTokenValue,
        estimatedFees = state.estimatedFees,
        memo = state.memo,
        nodeAddress = state.nodeAddress,
        confirmTitle = confirmTitle,
        hasFastSign = state.hasFastSign,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifyDepositScreen(
    fromAddress: String,
    srcTokenValue: String,
    estimatedFees: String,
    memo: String,
    nodeAddress: String,
    hasFastSign: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                if (hasFastSign) {
                    MultiColorButton(
                        text = stringResource(R.string.verify_transaction_fast_sign_btn_title),
                        textColor = Theme.colors.oxfordBlue800,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onFastSignClick,
                    )

                    UiSpacer(size = 16.dp)

                    MultiColorButton(
                        text = confirmTitle,
                        backgroundColor = Theme.colors.oxfordBlue800,
                        textColor = Theme.colors.turquoise800,
                        iconColor = Theme.colors.oxfordBlue800,
                        borderSize = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onConfirm
                    )
                } else {
                    MultiColorButton(
                        text = confirmTitle,
                        textColor = Theme.colors.oxfordBlue800,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onConfirm,
                    )
                }
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(it)
                .padding(all = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FormCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                ) {
                    AddressField(
                        title = stringResource(R.string.verify_transaction_from_title),
                        address = fromAddress,
                    )

                    OtherField(
                        title = stringResource(R.string.deposit_screen_amount_title),
                        value = srcTokenValue
                    )

                    AddressField(
                        title = stringResource(R.string.verify_deposit_memo_title),
                        address = memo,
                    )

                    if (nodeAddress.isNotBlank()) {
                        AddressField(
                            title = stringResource(R.string.verify_deposit_node_address_title),
                            address = nodeAddress,
                        )
                    }

                    OtherField(
                        title = stringResource(R.string.verify_deposit_gas_title),
                        value = estimatedFees,
                    )
                }
            }
        }

    }
}

@Preview
@Composable
private fun VerifyDepositScreenPreview() {
    VerifyDepositScreen(
        fromAddress = "123abc456bca",
        srcTokenValue = "0.00001 RUNE",
        estimatedFees = "0.02 RUNE",
        memo = "BOND:addressHere",
        nodeAddress = "123abc456bca",
        confirmTitle = "Sign",
        hasFastSign = false,
        onFastSignClick = {},
        onConfirm = {},
    )
}