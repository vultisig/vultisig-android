package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.swap.VerifyApproveUiModel
import com.vultisig.wallet.ui.models.swap.VerifyApproveViewModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.CheckField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VerifyApproveScreen(
    viewModel: VerifyApproveViewModel = hiltViewModel(),
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

    VerifyApproveScreen(
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConsentAllowance = viewModel::consentAllowance,
        onConfirm = viewModel::confirm,
    )
}

@Composable
internal fun VerifyApproveScreen(
    state: VerifyApproveUiModel,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentAllowance: (Boolean) -> Unit = {},
    onConfirm: () -> Unit,
) {
    VerifyApproveScreen(
        spenderAddress = state.spenderAddress,
        estimatedFees = state.estimatedFees,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        onConsentAllowance = onConsentAllowance,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifyApproveScreen(
    spenderAddress: String,
    estimatedFees: String,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentAllowance: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
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
                        title = stringResource(R.string.verify_approve_amount_title),
                        address = stringResource(R.string.verify_approve_amount_unlimited),
                    )

                    AddressField(
                        title = stringResource(R.string.verify_approve_spender_title),
                        address = spenderAddress
                    )

                    OtherField(
                        title = stringResource(R.string.verify_swap_screen_estimated_fees),
                        value = estimatedFees,
                    )
                }
            }

            if (isConsentsEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CheckField(
                        title = stringResource(R.string.verify_approve_consent_allowance),
                        isChecked = consentAllowance,
                        onCheckedChange = onConsentAllowance,
                    )
                }
            }
        }

        MultiColorButton(
            text = confirmTitle,
            textColor = Theme.colors.oxfordBlue800,
            minHeight = 44.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(all = 16.dp),
            onClick = onConfirm,
        )
    }
}

@Preview
@Composable
private fun VerifyApproveScreenPreview() {
    VerifyApproveScreen(
        spenderAddress = "0x1234567890",
        estimatedFees = "0.0001",
        consentAllowance = true,
        confirmTitle = "Confirm",
        onConsentAllowance = {},
        onConfirm = {},
    )
}