package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.components.library.form.FormEntry
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCardWithPercentage
import com.vultisig.wallet.ui.components.library.form.FormTitleCollapsibleTextField
import com.vultisig.wallet.ui.components.library.form.FormTokenSelection
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.text.SeparateNumberOutputTransformation


@Composable
internal fun SendFormScreen(
    vaultId: String,
    chainId: String?,
    startWithTokenId: String?,
    qrCodeResult: String?,
    viewModel: SendFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(vaultId, chainId, startWithTokenId) {
        viewModel.loadData(vaultId, chainId, startWithTokenId)
    }

    LaunchedEffect(qrCodeResult) {
        viewModel.setAddressFromQrCode(qrCodeResult)
    }

    LaunchedEffect(Unit) {
        viewModel.enableAdvanceGasUi()
    }

    val selectedChain = state.selectedCoin?.model?.address?.chain
    val specific = state.specific

    if (state.showGasSettings && selectedChain != null && specific != null) {
        EthGasSettingsScreen(
            navController = rememberNavController(),
            chain = selectedChain,
            specific = specific,
            onSaveGasSettings = viewModel::saveGasSettings,
            onDismissGasSettings = viewModel::dismissGasSettings,
        )
    }

    SendFormScreen(
        state = state,
        addressFieldState = viewModel.addressFieldState,
        tokenAmountFieldState = viewModel.tokenAmountFieldState,
        fiatAmountFieldState = viewModel.fiatAmountFieldState,
        memoFieldState = viewModel.memoFieldState,
        onDstAddressLostFocus = viewModel::validateDstAddress,
        onTokenAmountLostFocus = viewModel::validateTokenAmount,
        onDismissError = viewModel::dismissError,
        onSelectToken = viewModel::openTokenSelection,
        onSetOutputAddress = viewModel::setOutputAddress,
        onChooseMaxTokenAmount = viewModel::chooseMaxTokenAmount,
        onChoosePercentageAmount = viewModel::choosePercentageAmount,
        onScan = viewModel::scanAddress,
        onAddressBookClick = viewModel::openAddressBook,
        onSend = viewModel::send,
    )
}


@Composable
internal fun SendFormScreen(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    memoFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onSelectToken: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onChooseMaxTokenAmount: () -> Unit = {},
    onChoosePercentageAmount: (Float) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScan: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }


    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(all = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {

            FormTokenSelection(
                selectedToken = state.selectedCoin,
                onSelectToken = onSelectToken
            )

            FormEntry(
                title = stringResource(R.string.send_from_address),
            ) {
                Text(
                    text = state.from,
                    color = Theme.colors.neutral100,
                    style = Theme.menlo.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 16.dp
                        ),
                )
            }

            FormTextFieldCard(
                title = stringResource(R.string.send_to_address),
                hint = stringResource(R.string.send_to_address_hint),
                keyboardType = KeyboardType.Text,
                textFieldState = addressFieldState,
                onLostFocus = onDstAddressLostFocus,
                error = state.dstAddressError
            ) {
                val clipboard = LocalClipboardManager.current

                UiIcon(
                    drawableResId = R.drawable.ic_paste,
                    size = 20.dp,
                    onClick = {
                        clipboard.getText()
                            ?.toString()
                            ?.let(onSetOutputAddress)
                    }
                )

                UiSpacer(size = 8.dp)

                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 20.dp,
                    onClick = onScan,
                )

                UiSpacer(size = 8.dp)

                UiIcon(
                    drawableResId = R.drawable.ic_bookmark,
                    size = 20.dp,
                    onClick = onAddressBookClick,
                )
            }

            if (state.hasMemo)
                FormTitleCollapsibleTextField(
                    title = stringResource(R.string.send_form_screen_memo_optional),
                    hint = "",
                    onLostFocus = {},
                    textFieldState = memoFieldState
                )

            val separateNumberOutputTransformation = remember {
                SeparateNumberOutputTransformation()
            }
            
            FormTextFieldCardWithPercentage(
                title = stringResource(R.string.send_amount),
                hint = stringResource(R.string.send_amount_hint),
                keyboardType = KeyboardType.Number,
                textFieldState = tokenAmountFieldState,
                onLostFocus = onTokenAmountLostFocus,
                outputTransformation = separateNumberOutputTransformation,
                error = state.tokenAmountError,
                onPercentClick = onChoosePercentageAmount
            ) {
                Text(
                    text = stringResource(R.string.send_screen_max),
                    color = Theme.colors.neutral100,
                    style = Theme.menlo.body1,
                    modifier = Modifier
                        .clickable(onClick = onChooseMaxTokenAmount),
                )
            }

            FormTextFieldCard(
                title = stringResource(R.string.send_amount_currency, state.fiatCurrency),
                hint = stringResource(R.string.send_amount_currency_hint),
                keyboardType = KeyboardType.Number,
                textFieldState = fiatAmountFieldState,
                outputTransformation = separateNumberOutputTransformation,
                error = null
            )
            if (state.showGasFee) {
                FormDetails(
                    modifier = Modifier
                        .fillMaxWidth(),
                    title = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = Theme.colors.neutral100,
                                fontSize = Theme.menlo.body1.fontSize,
                                fontFamily = Theme.menlo.body1.fontFamily,
                                fontWeight = Theme.menlo.body1.fontWeight,

                                )
                        ) {
                            append(stringResource(R.string.send_form_network_fee))
                        }
                    },
                    value = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = Theme.colors.neutral100,
                                fontSize = Theme.menlo.body1.fontSize,
                                fontFamily = Theme.menlo.body1.fontFamily,
                            )
                        ) {
                            append(state.totalGas.asString())
                        }
                        withStyle(
                            style = SpanStyle(
                                color = Theme.colors.neutral400,
                                fontSize = Theme.menlo.body1.fontSize,
                                fontFamily = Theme.menlo.body1.fontFamily,
                            )
                        ) {
                            append(" (~${state.estimatedFee.asString()})")
                        }
                    }
                )
            }
            UiSpacer(size = 80.dp)

        }


        MultiColorButton(
            text = stringResource(R.string.send_continue_button),
            textColor = Theme.colors.oxfordBlue800,
            minHeight = 44.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(all = 16.dp),
            isLoading = state.isLoading,
            onClick = {
                focusManager.clearFocus()
                onSend()
            },
        )
    }

}


@Preview
@Composable
private fun SendFormScreenPreview() {
    SendFormScreen(
        state = SendFormUiModel(
            totalGas = UiText.DynamicString("12.5 Eth"),
            showGasFee = true,
            estimatedFee = UiText.DynamicString("$3.4"),
        ),
        addressFieldState = TextFieldState(),
        tokenAmountFieldState = TextFieldState(),
        fiatAmountFieldState = TextFieldState(),
        memoFieldState = TextFieldState(),
    )
}