package com.vultisig.wallet.ui.screens.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.models.sign.VerifySignMessageUiModel
import com.vultisig.wallet.ui.models.sign.VerifySignMessageViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySignMessageScreen(
    viewModel: VerifySignMessageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess = viewModel::authFastSign,
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

    VerifySignMessageScreen(
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConfirm = viewModel::confirm,
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
    )
}

@Composable
internal fun VerifySignMessageScreen(
    state: VerifySignMessageUiModel,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    val transactionUiModel = state.model
    VerifySignMessageScreen(
        method = transactionUiModel.method,
        message = transactionUiModel.message,
        confirmTitle = confirmTitle,
        hasFastSign = state.hasFastSign,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifySignMessageScreen(
    method: String,
    message: String,
    hasFastSign: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                if (hasFastSign) {
                    VsButton(
                        label = stringResource(R.string.verify_transaction_fast_sign_btn_title),
                        onClick = onFastSignClick,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )

                    UiSpacer(size = 16.dp)

                    VsButton(
                        label = confirmTitle,
                        onClick = onConfirm,
                        variant = VsButtonVariant.Secondary,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                } else {
                    VsButton(
                        label = confirmTitle,
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                }
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
                .verticalScroll(rememberScrollState()),
        ) {

            SignMessageBox(
                title = stringResource(R.string.verify_sign_message_method_field_title),
                value = method,
            )


            SignMessageBox(
                title = stringResource(R.string.verify_sign_message_message_field_title),
                value = message,
            )
        }
    }
}


@Composable
private fun SignMessageBox(
    title: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.caption
        )

        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    Theme.colors.backgrounds.disabled,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(
                    horizontal = 24.dp,
                    vertical = 20.dp,
                ),
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.body.s.medium
        )
    }
}


@Preview
@Composable
private fun VerifySignMessageScreenPreview() {
    VerifySignMessageScreen(
        method = "method",
        message = "message",
        confirmTitle = "Sign",
        hasFastSign = false,
        onFastSignClick = {},
        onConfirm = {},
    )
}