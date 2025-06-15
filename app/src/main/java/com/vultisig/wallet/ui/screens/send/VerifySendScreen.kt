package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.buttons.VsHoldableButton
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.screens.swap.SwapToken
import com.vultisig.wallet.ui.screens.swap.VerifyCardDetails
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySendScreen(
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
                onAuthorizationSuccess = viewModel::authFastSign,
            )
        }
    }

    VerifySendScreen(
        state = state,
        isConsentsEnabled = true,
        hasToolbar = true,
        confirmTitle = stringResource(R.string.keysign_paired_sign_title),
        onConsentAddress = viewModel::checkConsentAddress,
        onConsentAmount = viewModel::checkConsentAmount,
        onConsentDst = viewModel::checkConsentDst,
        onConfirm = viewModel::joinKeysign,
        onBackClick = viewModel::back,
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
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
    onConsentDst: (Boolean) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            if (hasToolbar) {
                VsTopAppBar(
                    title = "Send overview",
                    onBackClick = onBackClick,
                )
            }
        },
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(all = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {

                val tx = state.transaction

                Column(
                    modifier = Modifier
                        .background(
                            color = Theme.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(
                            all = 24.dp,
                        )
                ) {
                    Text(
                        text = "You're sending",
                        style = Theme.brockmann.headings.subtitle,
                        color = Theme.colors.text.light,
                    )

                    UiSpacer(24.dp)

                    SwapToken(
                        valuedToken = tx.token,
                    )

                    UiSpacer(12.dp)

                    VerifyCardDivider(8.dp)

                    VerifyCardDetails(
                        title = stringResource(R.string.verify_transaction_from_title),
                        subtitle = tx.srcAddress
                    )

                    VerifyCardDivider(0.dp)

                    VerifyCardDetails(
                        title = stringResource(R.string.verify_transaction_to_title),
                        subtitle = tx.dstAddress
                    )

                    if (tx.memo != null) {
                        VerifyCardDivider(0.dp)

                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_memo_title),
                            subtitle = tx.memo
                        )
                    }

                    if (state.functionName != null) {
                        VerifyCardDivider(0.dp)

                        VerifyCardDetails(
                            title = stringResource(R.string.deposit_screen_title),
                            subtitle = state.functionName
                        )
                    }

                    VerifyCardDivider(0.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 12.dp,
                            )
                    ) {
                        Text(
                            text = "Network",
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.colors.text.extraLight,
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
                                modifier = Modifier
                                    .size(16.dp),
                            )

                            Text(
                                text = chain.raw,
                                style = Theme.brockmann.supplementary.footnote,
                                color = Theme.colors.text.primary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }

                    VerifyCardDivider(0.dp)

                    UiSpacer(12.dp)

                    EstimatedNetworkFee(
                        tokenGas = "${tx.networkFee.value} ${tx.networkFee.token.ticker}",
                        fiatGas = tx.networkFee.fiatValue,
                    )
                }

                if (isConsentsEnabled) {
                    Column {
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

                        VsCheckField(
                            title = stringResource(R.string.verify_transaction_consent_correct_dst),
                            isChecked = state.consentDst,
                            onCheckedChange = onConsentDst,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp
                    )
            ) {
                if (state.hasFastSign) {
                    Text(
                        text = "Hold for paired sign",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.extraLight,
                        textAlign = TextAlign.Center,
                    )
                }

                VsHoldableButton(
                    label = "Sign transaction",
                    onLongClick = onConfirm,
                    onClick = if (state.hasFastSign) onFastSignClick else onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    )
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
private fun PreviewVerifySendScreen() {
    VerifySendScreen(
        state = VerifyTransactionUiModel(
            transaction = SendTxUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                memo = "some memo",
            ),
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