package com.vultisig.wallet.ui.screens.swap

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
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapViewModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.CheckField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VerifySwapScreen(
    viewModel: VerifySwapViewModel = hiltViewModel(),
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

    VerifySwapScreen(
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConsentReceiveAmount = viewModel::consentReceiveAmount,
        onConsentAmount = viewModel::consentAmount,
        onConfirm = viewModel::confirm,
        onConsentAllowance = viewModel::consentAllowance,
    )
}

@Composable
internal fun VerifySwapScreen(
    state: VerifySwapUiModel,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentReceiveAmount: (Boolean) -> Unit = {},
    onConsentAmount: (Boolean) -> Unit = {},
    onConsentAllowance: (Boolean) -> Unit = {},
    onConfirm: () -> Unit,
) {
    VerifySwapScreen(
        provider = state.provider.asString(),
        srcTokenValue = state.srcTokenValue,
        dstTokenValue = state.dstTokenValue,
        estimatedFees = state.estimatedFees,
        consentAmount = state.consentAmount,
        consentReceiveAmount = state.consentReceiveAmount,
        hasConsentAllowance = state.hasConsentAllowance,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        onConsentReceiveAmount = onConsentReceiveAmount,
        onConsentAmount = onConsentAmount,
        onConsentAllowance = onConsentAllowance,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifySwapScreen(
    provider: String,
    srcTokenValue: String,
    dstTokenValue: String,
    estimatedFees: String,
    consentAmount: Boolean,
    consentReceiveAmount: Boolean,
    hasConsentAllowance: Boolean,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentReceiveAmount: (Boolean) -> Unit,
    onConsentAmount: (Boolean) -> Unit,
    onConsentAllowance: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
        bottomBar = {
            MultiColorButton(
                text = confirmTitle,
                textColor = Theme.colors.oxfordBlue800,
                minHeight = 44.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
                onClick = onConfirm,
            )
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
                        address = srcTokenValue,
                    )

                    AddressField(
                        title = stringResource(R.string.verify_transaction_to_title),
                        address = dstTokenValue
                    )

                    if (hasConsentAllowance) {
                        AddressField(
                            title = stringResource(R.string.verify_approve_amount_title),
                            address = stringResource(R.string.verify_approve_amount_unlimited),
                        )
                    }

                    OtherField(
                        title = stringResource(R.string.verify_swap_screen_estimated_fees),
                        value = estimatedFees,
                        divider = false,
                    )
                }
            }

            if (isConsentsEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CheckField(
                        title = stringResource(R.string.verify_swap_consent_amount),
                        isChecked = consentAmount,
                        onCheckedChange = onConsentAmount,
                    )

                    CheckField(
                        title = stringResource(R.string.verify_swap_agree_receive_amount),
                        isChecked = consentReceiveAmount,
                        onCheckedChange = onConsentReceiveAmount,
                    )

                    if (hasConsentAllowance) {
                        CheckField(
                            title = stringResource(R.string.verify_swap_agree_allowance),
                            isChecked = consentAllowance,
                            onCheckedChange = onConsentAllowance,
                        )
                    }
                }
            }
        }

    }
}

@Preview
@Composable
private fun VerifySwapScreenPreview() {
    VerifySwapScreen(
        provider = "THORChain",
        srcTokenValue = "1 RUNE",
        dstTokenValue = "1 ETH",
        estimatedFees = "1.00$",
        consentAmount = true,
        consentReceiveAmount = false,
        hasConsentAllowance = true,
        consentAllowance = true,
        confirmTitle = "Sign",
        onConsentReceiveAmount = {},
        onConsentAmount = {},
        onConsentAllowance = {},
        onConfirm = {},
    )
}