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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CheckField
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapViewModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySwapScreen(
    viewModel: VerifySwapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess =  viewModel::authFastSign,
            )
        }
    }

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
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
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
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    VerifySwapScreen(
        provider = state.provider.asString(),
        swapTransactionUiModel = state.swapTransactionUiModel,
        hasAllConsents = state.hasAllConsents,
        consentAmount = state.consentAmount,
        consentReceiveAmount = state.consentReceiveAmount,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        hasFastSign = state.hasFastSign,
        onConsentReceiveAmount = onConsentReceiveAmount,
        onConsentAmount = onConsentAmount,
        onConsentAllowance = onConsentAllowance,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifySwapScreen(
    provider: String,
    swapTransactionUiModel: SwapTransactionUiModel,
    hasAllConsents: Boolean,
    consentAmount: Boolean,
    consentReceiveAmount: Boolean,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    hasFastSign: Boolean,
    onConsentReceiveAmount: (Boolean) -> Unit,
    onConsentAmount: (Boolean) -> Unit,
    onConsentAllowance: (Boolean) -> Unit,
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
                        disabled = isConsentsEnabled && !hasAllConsents,
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
                        disabled = isConsentsEnabled && !hasAllConsents,
                        onClick = onConfirm
                    )
                } else {
                    MultiColorButton(
                        text = confirmTitle,
                        textColor = Theme.colors.oxfordBlue800,
                        modifier = Modifier
                            .fillMaxWidth(),
                        disabled = isConsentsEnabled && !hasAllConsents,
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
                        address = swapTransactionUiModel.srcTokenValue,
                    )

                    AddressField(
                        title = stringResource(R.string.verify_transaction_to_title),
                        address = swapTransactionUiModel.dstTokenValue
                    )

                    if (swapTransactionUiModel.hasConsentAllowance) {
                        AddressField(
                            title = stringResource(R.string.verify_approve_amount_title),
                            address = swapTransactionUiModel.srcTokenValue,
                        )
                    }

                    OtherField(
                        title = stringResource(R.string.verify_swap_screen_total_fees),
                        value = swapTransactionUiModel.totalFee,
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

                    if (swapTransactionUiModel.hasConsentAllowance) {
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
        hasAllConsents = false,
        consentAmount = true,
        consentReceiveAmount = false,
        swapTransactionUiModel = SwapTransactionUiModel(
            srcTokenValue = "1 RUNE",
            dstTokenValue = "1 ETH",
            totalFee = "1.00$",
            hasConsentAllowance = true,
        ),
        consentAllowance = true,
        confirmTitle = "Sign",
        hasFastSign = false,
        onConsentReceiveAmount = {},
        onConsentAmount = {},
        onConsentAllowance = {},
        onFastSignClick = {},
        onConfirm = {},
    )
}