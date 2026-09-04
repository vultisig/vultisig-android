package com.vultisig.wallet.ui.screens.sign

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
import com.vultisig.wallet.ui.components.SignMessageCard
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.buttons.FastSignPairedButtons
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.sign.DecodedCustomMessage
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.sign.VerifySignMessageUiModel
import com.vultisig.wallet.ui.models.sign.VerifySignMessageViewModel
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySignMessageScreen(viewModel: VerifySignMessageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit =
        remember(context) {
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
        hasToolbar = false,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConfirm = viewModel::confirm,
        onBackClick = {},
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
    hasToolbar: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit,
) {
    val transactionUiModel = state.model
    VerifySignMessageScreen(
        method = transactionUiModel.method,
        message = transactionUiModel.message,
        decoded = transactionUiModel.decoded,
        confirmTitle = confirmTitle,
        hasFastSign = state.hasFastSign,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
        hasToolbar = hasToolbar,
        onBackClick = onBackClick,
    )
}

@Composable
private fun VerifySignMessageScreen(
    method: String,
    message: String,
    decoded: DecodedCustomMessage?,
    hasFastSign: Boolean,
    hasToolbar: Boolean,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (hasToolbar) {
                VsTopAppBar(
                    title = stringResource(R.string.verify_transaction_screen_title),
                    onBackClick = onBackClick,
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(all = 16.dp)) {
                if (hasFastSign) {
                    FastSignPairedButtons(
                        onFastSignClick = onFastSignClick,
                        onPairedSignClick = onConfirm,
                    )
                } else {
                    VsButton(
                        label = confirmTitle,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.padding(it)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            SignMessageCard(
                title = stringResource(R.string.verify_sign_message_signing_method),
                value = method,
            )

            // What the payload turned out to be, when it could be read. Shown above the payload
            // rather than in place of it, so nothing that gets signed is ever hidden behind a
            // friendlier rendering of it.
            when (decoded) {
                is DecodedCustomMessage.Text ->
                    SignMessageCard(
                        title = stringResource(R.string.verify_sign_message_decoded_message),
                        value = decoded.value,
                    )

                is DecodedCustomMessage.ContractCall -> {
                    SignMessageCard(
                        title =
                            stringResource(R.string.verify_transaction_function_signature_title),
                        value = decoded.function,
                    )
                    decoded.arguments?.let { arguments ->
                        SignMessageCard(
                            title =
                                stringResource(R.string.verify_transaction_function_inputs_title),
                            value = arguments,
                        )
                    }
                }

                // A digest is named by the card below rather than described by one of its own.
                DecodedCustomMessage.Hash,
                null -> Unit
            }

            SignMessageCard(
                title =
                    stringResource(
                        if (decoded is DecodedCustomMessage.Hash) {
                            R.string.verify_sign_message_message_hash
                        } else {
                            R.string.verify_sign_message_message_sign
                        }
                    ),
                value = message,
            )
        }
    }
}

@Preview
@Composable
private fun VerifySignMessageScreenPreview() {
    VerifySignMessageScreen(
        method = "method",
        message = "message",
        decoded = null,
        confirmTitle = "Sign",
        hasFastSign = false,
        hasToolbar = false,
        onFastSignClick = {},
        onConfirm = {},
        onBackClick = {},
    )
}

@Preview
@Composable
private fun JoinKeysignSignMessageVerifyPreview() {
    VerifySignMessageScreen(
        state =
            VerifySignMessageUiModel(
                model =
                    SignMessageTransactionUiModel(
                        method = "personal_sign",
                        message = "Sign in to Uniswap",
                    )
            ),
        hasToolbar = true,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onBackClick = {},
        onFastSignClick = {},
        onConfirm = {},
    )
}
