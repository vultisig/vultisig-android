package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.SignSolanaDisplayView
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsHoldableButton
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBadget
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.screens.swap.VerifyCardDetails
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.screens.swap.VerifyCardJsonDetails
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import vultisig.keysign.v1.SignSolana

@Composable
internal fun VerifySendScreen(viewModel: VerifyTransactionViewModel = hiltViewModel()) {
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
    val authorize: () -> Unit =
        remember(context) {
            {
                context.launchBiometricPrompt(
                    promptTitle = promptTitle,
                    onAuthorizationSuccess = viewModel::authFastSign,
                )
            }
        }

    LaunchedEffect(Unit) {
        viewModel.fastSignFlow.collect { shouldShowPrompt ->
            if (shouldShowPrompt) {
                authorize()
            }
        }
    }

    VerifySendScreen(
        state = state,
        isConsentsEnabled = true,
        hasToolbar = true,
        confirmTitle = stringResource(R.string.keysign_sign_transaction),
        onConsentAddress = viewModel::checkConsentAddress,
        onConsentAmount = viewModel::checkConsentAmount,
        onConfirm = viewModel::joinKeySign,
        onBackClick = viewModel::back,
        onFastSignClick = viewModel::fastSign,
        onConfirmScanning = viewModel::onConfirmScanning,
        onDismissScanning = viewModel::dismissScanningWarning,
    )
}

@Composable
internal fun VerifySendScreen(
    state: VerifyTransactionUiModel,
    hasToolbar: Boolean = false,
    isConsentsEnabled: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onConsentAddress: (Boolean) -> Unit = {},
    onConsentAmount: (Boolean) -> Unit = {},
    onBackClick: () -> Unit = {},
    onConfirmScanning: () -> Unit = {},
    onDismissScanning: () -> Unit = {},
) {
    V2Scaffold(
        title = stringResource(R.string.verify_send_send_overview).takeIf { hasToolbar },
        onBackClick = onBackClick.takeIf { hasToolbar },
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                if (
                    state.showScanningWarning && state.txScanStatus is TransactionScanStatus.Scanned
                ) {
                    SecurityScannerBottomSheet(
                        securityScannerModel = state.txScanStatus.result,
                        onContinueAnyway = onConfirmScanning,
                        onDismissRequest = onDismissScanning,
                    )
                }
                if (state.hasFastSign) {
                    Text(
                        text = stringResource(R.string.verify_deposit_hold_paired),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.tertiary,
                        textAlign = TextAlign.Center,
                    )
                    VsHoldableButton(
                        label = stringResource(R.string.verify_swap_sign_button),
                        onLongClick = onConfirm,
                        onClick = onFastSignClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            if (isConsentsEnabled && !state.hasAllConsents) {
                                VsButtonState.Disabled
                            } else {
                                VsButtonState.Enabled
                            },
                    )
                } else {
                    val buttonState =
                        if (isConsentsEnabled && !state.hasAllConsents) VsButtonState.Disabled
                        else VsButtonState.Enabled
                    VsButton(
                        label = confirmTitle,
                        state = buttonState,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                val tx = state.transaction

                SecurityScannerBadget(state.txScanStatus)

                Column(
                    modifier =
                        Modifier.background(
                                color = Theme.v2.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(all = 24.dp)
                ) {
                    // Title-only hero until Blockaid simulation is wired into mobile;
                    // decode-derived amounts can mislead without a simulation backing them.
                    UiSpacer(12.dp)

                    if (tx.functionName != null) {
                        Text(
                            text = tx.functionName,
                            style = Theme.brockmann.headings.title3,
                            color = Theme.v2.colors.text.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        VsOverviewToken(
                            header = stringResource(R.string.verify_deposit_sending),
                            valuedToken = tx.token,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                            withContainer = false,
                        )
                    }

                    UiSpacer(12.dp)

                    VerifyCardDivider(8.dp)

                    VerifyCardDetails(
                        title = stringResource(R.string.verify_transaction_from_title),
                        subtitle = tx.srcVaultName ?: tx.srcAddress,
                        bracketValue = tx.srcVaultName?.let { tx.srcAddress },
                    )

                    VerifyCardDivider(0.dp)

                    val toDstLabel = tx.dstVaultName ?: tx.dstAddressBookTitle ?: tx.dstLabel
                    VerifyCardDetails(
                        title = stringResource(R.string.verify_transaction_to_title),
                        subtitle = toDstLabel ?: tx.dstAddress,
                        bracketValue = toDstLabel?.let { tx.dstAddress },
                    )

                    if (tx.memo != null) {
                        VerifyCardDivider(0.dp)

                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_memo_title),
                            subtitle = tx.memo,
                        )
                    }
                    tx.signAmino
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            VerifyCardDivider(0.dp)

                            VerifyCardJsonDetails(
                                title = stringResource(R.string.amino_sign),
                                subtitle = tx.signAmino,
                            )
                        }

                    tx.signDirect
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            VerifyCardDivider(0.dp)

                            VerifyCardJsonDetails(
                                title = stringResource(R.string.amino_direct),
                                subtitle = tx.signDirect,
                            )
                        }
                    tx.signSolana
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            VerifyCardDivider(0.dp)

                            SignSolanaDisplayView(
                                signSolana = SignSolana(rawTransactions = listOf(it))
                            )
                        }

                    if (tx.tokenDisplay != null) {
                        VerifyCardDivider(0.dp)

                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_amount_title),
                            subtitle = tx.tokenDisplay,
                        )
                    }
                    if (tx.functionSignature != null || tx.functionInputs != null) {
                        VerifyCardDivider(0.dp)
                        TransactionDetailsSection(
                            functionSignature = tx.functionSignature,
                            functionInputs = tx.functionInputs,
                        )
                    }

                    VerifyCardDivider(0.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.verify_deposit_network),
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.tertiary,
                            maxLines = 1,
                        )

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val chain = state.transaction.token.token.chain

                            Image(
                                painter = painterResource(chain.logo),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )

                            Text(
                                text = chain.raw,
                                style = Theme.brockmann.body.s.medium,
                                color = Theme.v2.colors.text.primary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }

                    VerifyCardDivider(0.dp)

                    UiSpacer(12.dp)

                    EstimatedNetworkFee(
                        tokenGas = tx.networkFeeTokenValue,
                        fiatGas = tx.networkFeeFiatValue,
                        isLoading = state.isLoadingFees,
                    )
                }

                if (isConsentsEnabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        VsCheckField(
                            title = stringResource(R.string.verify_transaction_consent_address),
                            isChecked = state.consentAddress,
                            onCheckedChange = onConsentAddress,
                        )

                        VsCheckField(
                            title = stringResource(R.string.verify_transaction_consent_amount),
                            isChecked = state.consentAmount,
                            onCheckedChange = onConsentAmount,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun AddressField(title: String, address: String, divider: Boolean = true) {
    Column {
        Text(
            text = title,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.headings.subtitle,
        )

        UiSpacer(size = 16.dp)

        Text(
            text = address,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        if (divider) {
            UiSpacer(size = 12.dp)

            UiHorizontalDivider()
        }
    }
}

@Composable
private fun TransactionDetailsSection(functionSignature: String?, functionInputs: String?) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.tx_done_transaction_details),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )

            IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(16.dp)) {
                UiIcon(
                    drawableResId = R.drawable.chevron,
                    tint = Theme.v2.colors.text.tertiary,
                    size = 8.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(
                            color = Theme.v2.colors.variables.bordersLight,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                functionSignature?.let {
                    VerifyCardJsonDetails(
                        title = stringResource(R.string.deposit_screen_title),
                        subtitle = it,
                    )
                }

                functionInputs?.let {
                    VerifyCardJsonDetails(
                        title = stringResource(R.string.verify_transaction_function_inputs_title),
                        subtitle = it,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OtherField(title: String, value: String, divider: Boolean = true) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Text(
                text = title,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
            )

            UiSpacer(weight = 1f)
            UiSpacer(size = 8.dp)

            Text(
                text = value,
                textAlign = TextAlign.End,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )
        }

        if (divider) {
            UiHorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun PreviewVerifySendScreen() {
    VerifySendScreen(
        state =
            VerifyTransactionUiModel(
                transaction =
                    TransactionDetailsUiModel(
                        srcAddress = "0x1234567890",
                        dstAddress = "0x1234567890",
                        memo = "some memo",
                        signAmino =
                            """
                            {
                               "type": "osmosis/smartaccount/add-authenticator",
                               "value": {
                                   "authenticator_type": "AllOf",
                                   "data": "W3sidHlwZSI6IlNpZ25hdHVyZVZlcmlmaWNhdGlvbiIsImNvbmZpZyI6IkF3ZTJjZEZtM1hqM0VVWEg0WTBpWDhGVTNGMElKNnV3R3F3TktsenVLSmFwIn0seyJ0eXBlIjoiQ29zbXdhc21BdXRoZW50aWNhdG9yVjEiLCJjb25maWciOiJleUpqYjI1MGNtRmpkQ0k2SUNKdmMyMXZNVEI0Y1hZNGNteHdhMlpzZVhkdE9USnJOWGRrYlhCc2VuazNhMmgwWVhOc09XTXlZekE0Y0hOdGRteDFOVFF6YXpjeU5ITjVPVFJyTnpRaUxDQWljR0Z5WVcxeklqb2dJbVY1U25OaFZ6RndaRU5KTmtscVZYZE5SRUYzVFVSQmQwMUVRV2xNUTBwNVdsaE9iR1JHT1hkYVdFcHdZakpSYVU5cFNtdFpXR3RwVEVOS01HRlhNV3hZTW5od1lsZHNNRWxxY0RkSmJWWjFXa05KTmtscVJUTk9hbU40VG5wUk0wMUVXWGROUkVGM1RVUkJkMDFFUVdsbVdEQTlJbjA9In0seyJ0eXBlIjoiQW55T2YiLCJjb25maWciOiJXM3NpZEhsd1pTSTZJazFsYzNOaFoyVkdhV3gwWlhJaUxDSmpiMjVtYVdjaU9pSmxlVXBCWkVoc2QxcFRTVFpKYVRsMll6SXhkbU15YkhwTWJrSjJZako0ZEZsWE5XaGFNbFo1VEc1WmVGbHRWakJaVkVWMVZGaE9ibFV6WkdoalJWWTBXVmRPTUZGWE1YWmtWelV3VTFjMGFXWlJQVDBpZlN4N0luUjVjR1VpT2lKTlpYTnpZV2RsUm1sc2RHVnlJaXdpWTI5dVptbG5Jam9pWlhsS1FXUkliSGRhVTBrMlNXazVkbU15TVhaak1teDZURzVDZG1JeWVIUlpWelZvV2pKV2VVeHVXWGhaYlZZd1dWUkZkVlJZVG01Vk0wSnpZVmhTVTJJelZqQmFWazR6V1ZoQ1JtVkhSbXBrUlVaMFlqTldkV1JGYkhWSmJqQTlJbjBzZXlKMGVYQmxJam9pVFdWemMyRm5aVVpwYkhSbGNpSXNJbU52Ym1acFp5STZJbVY1U2tGa1NHeDNXbE5KTmtscE9YWmpNakYyWXpKc2VreHVRblppTW5oMFdWYzFhRm95Vm5sTWJsbDRXVzFXTUZsVVJYVlVXRTV1VlROa2FHTkZWalJaVjA0d1VWY3hkbVJYTlRCVU0xWXdTVzR3UFNKOUxIc2lkSGx3WlNJNklrMWxjM05oWjJWR2FXeDBaWElpTENKamIyNW1hV2NpT2lKbGVVcEJaRWhzZDFwVFNUWkphVGwyWXpJeGRtTXliSHBNYmtKMllqSjRkRmxYTldoYU1sWjVURzVaZUZsdFZqQlpWRVYxVkZoT2JsVXpRbk5oV0ZKVFlqTldNRnBXVGpOWldFSkdaVWRHYW1SRlJuUmlNMVoxWkVVNU1XUkRTamtpZlN4N0luUjVjR1VpT2lKTlpYTnpZV2RsUm1sc2RHVnlJaXdpWTI5dVptbG5Jam9pWlhsS1FXUkliSGRhVTBrMlNXazVkbU15TVhaak1teDZURzFPZG1KdFRteGlibEo1V1ZoU2JGcEhlSEJqV0Zad1drZHNNR1ZUTlRKTlYwcHNaRWRGZUV4ck1YcGFNV1J3WkVkb2EyTnRSak5WUnpsNllWaFNjR0l5TkdsbVVUMDlJbjBzZXlKMGVYQmxJam9pVFdWemMyRm5aVVpwYkhSbGNpSXNJbU52Ym1acFp5STZJbVY1U2tGa1NHeDNXbE5KTmtscE9YWmpNakYyWXpKc2VreHVXbWhpU0U1c1pFaENlVnBYV1hWa2FrWnBXbGhTYUUxVE5VNWpNbVJVV2xoU1YxbFhlSEJhUjBZd1lqTktWRnBZVWxGamJWWnRXbGhLYkdKdFRteEpiakE5SW4xZCJ9XQ==",
                                   "sender": "osmo1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqxf3l5h"
                               }
                            }
                            """
                                .trimIndent(),
                    )
            ),
        isConsentsEnabled = true,
        confirmTitle = stringResource(R.string.keysign_sign_transaction),
        onConsentAddress = {},
        onConsentAmount = {},
        onFastSignClick = {},
        onConfirm = {},
    )
}
