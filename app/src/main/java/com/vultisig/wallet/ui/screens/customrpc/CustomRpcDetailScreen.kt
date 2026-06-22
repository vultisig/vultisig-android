package com.vultisig.wallet.ui.screens.customrpc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
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
        onPaste = viewModel::onPaste,
        onSaveClick = viewModel::onSaveClick,
        onResetClick = viewModel::onResetClick,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun CustomRpcDetailScreen(
    state: CustomRpcDetailUiState,
    urlFieldState: TextFieldState,
    onPaste: (String) -> Unit,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.custom_rpc_detail_title, state.chainName),
        onBackClick = onBackClick,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.custom_rpc_endpoint_label),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )

            UiSpacer(size = 8.dp)

            RpcEndpointField(
                urlFieldState = urlFieldState,
                isError = state.errorMessage != null,
                onPaste = onPaste,
            )

            UiSpacer(size = 8.dp)

            Text(
                text =
                    state.errorMessage?.asString()
                        ?: stringResource(R.string.custom_rpc_endpoint_helper),
                style = Theme.brockmann.supplementary.footnote,
                color =
                    if (state.errorMessage != null) Theme.v2.colors.alerts.error
                    else Theme.v2.colors.text.primary,
            )

            if (state.hasExistingOverride && state.defaultEndpoint != null) {
                UiSpacer(size = 24.dp)
                DefaultEndpointCard(endpoint = state.defaultEndpoint)
            }

            UiSpacer(weight = 1f)

            VsButton(
                label = stringResource(R.string.custom_rpc_save_rpc),
                variant = VsButtonVariant.Primary,
                state =
                    if (state.canSave && !state.isSaving) VsButtonState.Enabled
                    else VsButtonState.Disabled,
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.hasExistingOverride) {
                UiSpacer(size = 12.dp)
                VsButton(
                    label = stringResource(R.string.custom_rpc_reset_button),
                    variant = VsButtonVariant.Secondary,
                    state = if (state.isSaving) VsButtonState.Disabled else VsButtonState.Enabled,
                    onClick = onResetClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun RpcEndpointField(
    urlFieldState: TextFieldState,
    isError: Boolean,
    onPaste: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(
                    color = Theme.v2.colors.backgrounds.surface1,
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 1.dp,
                    color =
                        if (isError) Theme.v2.colors.alerts.error else Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicTextField(
            state = urlFieldState,
            modifier = Modifier.weight(1f),
            textStyle = Theme.brockmann.body.s.medium.copy(color = Theme.v2.colors.text.primary),
            cursorBrush = SolidColor(Theme.v2.colors.primary.accent4),
        )
        PasteIcon(size = 20.dp, onPaste = onPaste)
    }
}

@Composable
private fun DefaultEndpointCard(endpoint: String) {
    Column {
        Text(
            text = stringResource(R.string.custom_rpc_default_endpoint_label),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        UiSpacer(size = 8.dp)
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = Theme.v2.colors.backgrounds.surface1,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp)
        ) {
            Text(
                text = endpoint,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

@Preview
@Composable
private fun CustomRpcDetailNewPreview() {
    CustomRpcDetailScreen(
        state = CustomRpcDetailUiState(chainName = "Ethereum", canSave = true),
        urlFieldState = TextFieldState("https://eth-mainnet.g.alchemy.com/v2/your-api-key"),
        onPaste = {},
        onSaveClick = {},
        onResetClick = {},
        onBackClick = {},
    )
}

@Preview
@Composable
private fun CustomRpcDetailEditPreview() {
    CustomRpcDetailScreen(
        state =
            CustomRpcDetailUiState(
                chainName = "Ethereum",
                defaultEndpoint = "https://ethereum.publicnode.com",
                hasExistingOverride = true,
                canSave = true,
            ),
        urlFieldState = TextFieldState("https://eth-mainnet.g.alchemy.com/v2/your-api-key"),
        onPaste = {},
        onSaveClick = {},
        onResetClick = {},
        onBackClick = {},
    )
}
