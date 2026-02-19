package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.transaction.AddAddressEntryUiModel
import com.vultisig.wallet.ui.models.transaction.AddressEntryViewModel
import com.vultisig.wallet.ui.models.NetworkUiModel
import com.vultisig.wallet.ui.models.toNetworkUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun AddAddressEntryScreen(
    navController: NavController,
    model: AddressEntryViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    AddAddressEntryScreen(
        state = state,
        titleTextFieldState = model.titleTextFieldState,
        addressTextFieldState = model.addressTextFieldState,
        onSelectChainClick = model::selectChain,
        onSaveAddressClick = model::saveAddress,
        onSetOutputAddress = model::setOutputAddress,
        onScan = model::scanAddress,
        onBackClick = navController::popBackStack,
    )
}


@Preview
@Composable
private fun AddAddressEntryScreenPreview() {
    AddAddressEntryScreen(
        state = AddAddressEntryUiModel(),
        titleTextFieldState = rememberTextFieldState(),
        addressTextFieldState = rememberTextFieldState(),
    )
}


@Composable
internal fun AddAddressEntryScreen(
    state: AddAddressEntryUiModel,
    titleTextFieldState: TextFieldState,
    addressTextFieldState: TextFieldState,
    onSelectChainClick: (NetworkUiModel) -> Unit = {},
    onSaveAddressClick: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {

    V2Scaffold(
        title = stringResource(state.titleRes),
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.add_vault_save),
                onClick = onSaveAddressClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 24.dp,
                        horizontal = 16.dp,
                    ),
            )
        }
    ) {
        Column {

            SelectChain(
                selectedChain = state.selectedChain,
                modifier = Modifier.clickOnce(
                    onClick = {
                        onSelectChainClick(state.selectedChain)
                    }
                )
            )

            UiSpacer(
                size = 12.dp
            )

            VsTextInputField(
                label = stringResource(R.string.add_address_title_label),
                hint = stringResource(R.string.add_address_type_hint),
                textFieldState = titleTextFieldState,
                keyboardType = KeyboardType.Text,
                footNote = state.titleError?.asString(),
                innerState = if (state.titleError != null)
                    VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default
            )


            UiSpacer(
                size = 12.dp
            )
            val clipboardData = VsClipboardService.getClipboardData()

            VsTextInputField(
                label = stringResource(R.string.add_address_address_title),
                hint = stringResource(R.string.add_address_type_hint),
                textFieldState = addressTextFieldState,
                trailingIcon = R.drawable.camera,
                onTrailingIconClick = onScan,
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                trailingIcon2 = R.drawable.copy2,
                onTrailingIcon2Click = {
                    clipboardData.value?.let { clipBoard ->
                        onSetOutputAddress(clipBoard)
                    }
                },
                footNote = state.addressError?.asString(),
                innerState = if (state.addressError != null)
                    VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default
            )


        }
    }
}


@Composable
internal fun SelectChain(
    modifier: Modifier = Modifier,
    selectedChain: NetworkUiModel?
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.select_chain_chain_title),
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.s.medium,
        )

        UiSpacer(
            size = 8.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(size = 12.dp)
                )
                .background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(size = 12.dp)
                )
                .padding(
                    all = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (selectedChain == null) {
                Text(
                    text = stringResource(R.string.address_entry_select),
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            } else {
                TokenLogo(
                    logo = selectedChain.logo,
                    title = selectedChain.title,
                    modifier = Modifier
                        .size(32.dp),
                    errorLogoModifier = Modifier
                        .size(32.dp)
                )
                UiSpacer(size = 6.dp)
                Text(
                    text = selectedChain.title,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }

            UiSpacer(
                weight = 1f
            )


            UiIcon(
                drawableResId = R.drawable.ic_caret_right,
                size = 16.dp,
            )
        }

    }
}

private class SelectChainPreviewParameterProvider : PreviewParameterProvider<NetworkUiModel?> {
    override val values: Sequence<NetworkUiModel?>
        get() = sequenceOf(
            null,
            Chain.Ethereum.toNetworkUiModel(),
        )
}

@Preview
@Composable
private fun SelectChainPreview(
    @PreviewParameter(SelectChainPreviewParameterProvider::class)
    chain: NetworkUiModel?,
) {
    SelectChain(selectedChain = chain)
}