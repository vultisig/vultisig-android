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
import androidx.compose.material3.Scaffold
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
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.transaction.AddAddressEntryUiModel
import com.vultisig.wallet.ui.models.transaction.AddressEntryViewModel
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
    onSelectChainClick: (Chain) -> Unit = {},
    onSaveAddressClick: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(state.titleRes),
                onBackClick = onBackClick,
            )
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.add_vault_save),
                onClick = onSaveAddressClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 16.dp,
                    ),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(
                    vertical = 12.dp,
                    horizontal = 16.dp,
                )
        ) {

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
    selectedChain: Chain?
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.select_chain_chain_title),
            color = Theme.colors.text.primary,
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
                    color = Theme.colors.borders.light,
                    shape = RoundedCornerShape(size = 12.dp)
                )
                .background(
                    color = Theme.colors.backgrounds.secondary,
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
                    color = Theme.colors.text.extraLight,
                )
            } else {
                TokenLogo(
                    logo = selectedChain.logo,
                    title = selectedChain.raw,
                    modifier = Modifier
                        .size(32.dp),
                    errorLogoModifier = Modifier
                        .size(32.dp)
                )
                UiSpacer(size = 6.dp)
                Text(
                    text = selectedChain.raw,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.colors.text.primary,
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

private class SelectChainPreviewParameterProvider : PreviewParameterProvider<Chain?> {
    override val values: Sequence<Chain?>
        get() = sequenceOf(
            null,
            Chain.Ethereum,
        )
}

@Preview
@Composable
private fun SelectChainPreview(
    @PreviewParameter(SelectChainPreviewParameterProvider::class)
    chain: Chain?,
) {
    SelectChain(selectedChain = chain)
}