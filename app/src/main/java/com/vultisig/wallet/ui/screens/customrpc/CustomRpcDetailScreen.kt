package com.vultisig.wallet.ui.screens.customrpc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.RpcHealthResult
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.customrpc.CustomRpcDetailUiState
import com.vultisig.wallet.ui.models.customrpc.CustomRpcDetailViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun CustomRpcDetailScreen() {
    val viewModel = hiltViewModel<CustomRpcDetailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CustomRpcDetailScreen(
        state = state,
        urlFieldState = viewModel.urlFieldState,
        onTestClick = viewModel::onTestClick,
        onSaveClick = viewModel::onSaveClick,
        onResetClick = viewModel::onResetClick,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun CustomRpcDetailScreen(
    state: CustomRpcDetailUiState,
    urlFieldState: TextFieldState,
    onTestClick: () -> Unit,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = state.chainName, onBackClick = onBackClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            VsTextInputField(
                textFieldState = urlFieldState,
                label = stringResource(R.string.custom_rpc_url_label),
                hint = stringResource(R.string.custom_rpc_url_hint),
                footNote = state.errorMessage?.asString(),
                innerState =
                    if (state.errorMessage != null) VsTextInputFieldInnerState.Error
                    else VsTextInputFieldInnerState.Default,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = 12.dp)

            TestResultLabel(isTesting = state.isTesting, result = state.testResult)

            UiSpacer(weight = 1f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VsButton(
                    label = stringResource(R.string.custom_rpc_test_button),
                    variant = VsButtonVariant.Secondary,
                    state =
                        if (state.canSave && !state.isTesting) VsButtonState.Enabled
                        else VsButtonState.Disabled,
                    onClick = onTestClick,
                    modifier = Modifier.weight(1f),
                )
                VsButton(
                    label = stringResource(R.string.custom_rpc_save_button),
                    variant = VsButtonVariant.Primary,
                    state = if (state.canSave) VsButtonState.Enabled else VsButtonState.Disabled,
                    onClick = onSaveClick,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.hasExistingOverride) {
                UiSpacer(size = 12.dp)
                VsButton(
                    label = stringResource(R.string.custom_rpc_reset_button),
                    variant = VsButtonVariant.Error,
                    onClick = onResetClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun TestResultLabel(isTesting: Boolean, result: RpcHealthResult?) {
    when {
        isTesting ->
            Text(
                text = stringResource(R.string.custom_rpc_testing),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )

        result is RpcHealthResult.Reachable -> {
            val text =
                if (result.networkVerified) {
                    stringResource(R.string.custom_rpc_reachable, result.latencyMs)
                } else {
                    stringResource(R.string.custom_rpc_reachable_unverified, result.latencyMs)
                }
            Text(
                text = text,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.alerts.success,
            )
        }

        result is RpcHealthResult.WrongChain ->
            Text(
                text = stringResource(R.string.custom_rpc_wrong_chain),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.alerts.error,
            )

        result is RpcHealthResult.InvalidResponse ->
            Text(
                text = stringResource(R.string.custom_rpc_invalid_response),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.alerts.error,
            )

        result is RpcHealthResult.Unreachable ->
            Text(
                text = stringResource(R.string.custom_rpc_unreachable),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.alerts.error,
            )
    }
}

@Preview
@Composable
private fun CustomRpcDetailScreenPreview() {
    CustomRpcDetailScreen(
        state =
            CustomRpcDetailUiState(
                chainName = "Ethereum",
                hasExistingOverride = true,
                canSave = true,
                testResult = RpcHealthResult.Reachable(latencyMs = 142, networkVerified = true),
            ),
        urlFieldState = TextFieldState("https://my-node.example/eth"),
        onTestClick = {},
        onSaveClick = {},
        onResetClick = {},
        onBackClick = {},
    )
}
