package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BlowfishMessage
import com.vultisig.wallet.ui.components.CheckField
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifyTransactionScreen(
    viewModel: VerifyTransactionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)


    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(id = R.string.dialog_default_error_title),
            text = errorText.asString(),
            onDismiss = viewModel::dismissError,
        )
    }
    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess =  viewModel::authFastSign,
            )
        }
    }

    VerifyTransactionScreen(
        state = state,
        isConsentsEnabled = true,
        confirmTitle = stringResource(R.string.keysign_paired_sign_title),
        onConsentAddress = viewModel::checkConsentAddress,
        onConsentAmount = viewModel::checkConsentAmount,
        onConsentDst = viewModel::checkConsentDst,
        onConfirm = viewModel::joinKeysign,
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
    )
}

@Composable
internal fun VerifyTransactionScreen(
    state: VerifyTransactionUiModel,
    isConsentsEnabled: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onConsentAddress: (Boolean) -> Unit = {},
    onConsentAmount: (Boolean) -> Unit = {},
    onConsentDst: (Boolean) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                if (state.hasFastSign) {
                    MultiColorButton(
                        text = stringResource(R.string.verify_transaction_fast_sign_btn_title),
                        textColor = Theme.colors.oxfordBlue800,
                        modifier = Modifier
                            .fillMaxWidth(),
                        disabled = isConsentsEnabled && !state.hasAllConsents,
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
                        disabled = isConsentsEnabled && !state.hasAllConsents,
                        onClick = onConfirm
                    )
                } else {
                    MultiColorButton(
                        text = confirmTitle,
                        textColor = Theme.colors.oxfordBlue800,
                        modifier = Modifier
                            .fillMaxWidth(),
                        disabled = isConsentsEnabled && !state.hasAllConsents,
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
                        address = state.transaction.srcAddress
                    )

                    AddressField(
                        title = stringResource(R.string.verify_transaction_to_title),
                        address = state.transaction.dstAddress
                    )

                    if (!state.transaction.memo.isNullOrEmpty())
                        OtherField(
                            title = stringResource(R.string.verify_transaction_memo_title),
                            value = state.transaction.memo
                        )

                    OtherField(
                        title = stringResource(R.string.verify_transaction_amount_title),
                        value = state.transaction.tokenValue,
                    )

                    OtherField(
                        title = stringResource(R.string.verify_transaction_value),
                        value = state.transaction.fiatValue,
                    )

                    FormDetails(modifier = Modifier
                        .padding(
                            vertical = 12.dp,
                        )
                        .fillMaxWidth(),
                        title = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = Theme.colors.neutral100,
                                    fontSize = 14.sp,
                                    fontFamily = Theme.montserrat.subtitle1.fontFamily,
                                    fontWeight = Theme.montserrat.subtitle1.fontWeight,

                                    )
                            ) {
                                append(stringResource(R.string.verify_transaction_network_fee))
                            }
                        },
                        value = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = Theme.colors.neutral100,
                                    fontSize = 14.sp,
                                    fontFamily = Theme.montserrat.subtitle1.fontFamily,
                                    fontWeight = Theme.montserrat.subtitle1.fontWeight,
                                )
                            ) {
                                append(state.transaction.totalGas.toString())
                            }
                            withStyle(
                                style = SpanStyle(
                                    color = Theme.colors.neutral400,
                                    fontSize = 14.sp,
                                    fontFamily = Theme.montserrat.subtitle1.fontFamily,
                                    fontWeight = Theme.montserrat.subtitle1.fontWeight,
                                )
                            ) {
                                append(" (~${state.transaction.estimatedFee.toString()})")
                            }
                        }
                    )
                }
            }

            if (isConsentsEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CheckField(
                        title = stringResource(R.string.verify_transaction_consent_address),
                        isChecked = state.consentAddress,
                        onCheckedChange = onConsentAddress,
                    )

                    CheckField(
                        title = stringResource(R.string.verify_transaction_consent_amount),
                        isChecked = state.consentAmount,
                        onCheckedChange = onConsentAmount,
                    )

                    CheckField(
                        title = stringResource(R.string.verify_transaction_consent_correct_dst),
                        isChecked = state.consentDst,
                        onCheckedChange = onConsentDst,
                    )
                }
            }

            BlowfishMessage(
                isShow = state.blowfishShow,
                warnings = state.blowfishWarnings,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
internal fun AddressField(
    title: String,
    address: String,
    divider: Boolean = true,
) {
    Column {
        Text(
            text = title,
            color = Theme.colors.neutral100,
            style = Theme.montserrat.heading5,
        )

        UiSpacer(size = 16.dp)

        Text(
            text = address,
            style = Theme.montserrat.subtitle3,
            color = Theme.colors.turquoise800,
        )

        if (divider) {
            UiSpacer(size = 12.dp)

            UiHorizontalDivider()
        }
    }
}

@Composable
internal fun OtherField(
    title: String,
    value: String,
    divider: Boolean = true,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                vertical = 12.dp,
            )
        ) {
            Text(
                text = title,
                color = Theme.colors.neutral100,
                style = Theme.montserrat.subtitle1,
            )

            UiSpacer(weight = 1f)
            UiSpacer(size = 8.dp)

            Text(
                text = value,
                textAlign = TextAlign.End,
                color = Theme.colors.neutral100,
                style = Theme.menlo.subtitle1,
            )
        }

        if (divider) {
            UiHorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun VerifyTransactionScreenPreview() {
    VerifyTransactionScreen(
        state = VerifyTransactionUiModel(
            transaction = TransactionUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                tokenValue = "1.1",
                fiatValue = "1.1",
                fiatCurrency = "USD",
                gasFeeValue = "1.1",
                memo = "some memo",
                estimatedFee = "0.75 USd",
                totalGas = "0.00031361"
            ),
            blowfishShow = true,
            hasFastSign = true,
        ),
        isConsentsEnabled = true,
        confirmTitle = stringResource(R.string.keysign_paired_sign_title),
        onConsentAddress = {},
        onConsentAmount = {},
        onConsentDst = {},
        onFastSignClick = {},
        onConfirm = {},
    )
}