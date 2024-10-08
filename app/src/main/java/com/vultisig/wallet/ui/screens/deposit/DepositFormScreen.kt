package com.vultisig.wallet.ui.screens.deposit

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.deposit.DepositChain
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun DepositFormScreen(
    model: DepositFormViewModel = hiltViewModel(),
    vaultId: String,
    chainId: String,
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) {
        model.loadData(vaultId, chainId)
    }

    DepositFormScreen(
        state = state,
        tokenAmountFieldState = model.tokenAmountFieldState,
        nodeAddressFieldState = model.nodeAddressFieldState,
        providerFieldState = model.providerFieldState,
        operatorFeeFieldState = model.operatorFeeFieldState,
        customMemoFieldState = model.customMemoFieldState,
        assetsFieldState = model.assetsFieldState,
        lpUnitsFieldState = model.lpUnitsFieldState,
        onAssetsLostFocus = model::validateAssets,
        onLpUnitsLostFocus = model::validateLpUnits,
        onTokenAmountLostFocus = model::validateTokenAmount,
        onNodeAddressLostFocus = model::validateNodeAddress,
        onProviderLostFocus = model::validateProvider,
        onOperatorFeeLostFocus = model::validateOperatorFee,
        onSelectDepositOption = model::selectDepositOption,
        onCustomMemoLostFocus = model::validateCustomMemo,
        basisPointsFieldState = model.basisPointsFieldState,
        onBasisPointsLostFocus = model::validateBasisPoints,
        onDismissError = model::dismissError,
        onSetNodeAddress = model::setNodeAddress,
        onSetProvider = model::setProvider,
        onScan = model::scan,
        onDeposit = model::deposit,
    )
}

@Composable
internal fun DepositFormScreen(
    state: DepositFormUiModel,
    tokenAmountFieldState: TextFieldState,
    nodeAddressFieldState: TextFieldState,
    providerFieldState: TextFieldState,
    operatorFeeFieldState: TextFieldState,
    customMemoFieldState: TextFieldState,
    assetsFieldState: TextFieldState,
    lpUnitsFieldState: TextFieldState,
    onTokenAmountLostFocus: () -> Unit = {},
    onAssetsLostFocus: () -> Unit = {},
    onLpUnitsLostFocus: () -> Unit = {},
    onNodeAddressLostFocus: () -> Unit = {},
    onProviderLostFocus: () -> Unit = {},
    onOperatorFeeLostFocus: () -> Unit = {},
    onCustomMemoLostFocus: () -> Unit = {},
    basisPointsFieldState: TextFieldState,
    onBasisPointsLostFocus: () -> Unit = {},
    onSelectDepositOption: (DepositOption) -> Unit = {},
    onDismissError: () -> Unit = {},
    onSetNodeAddress: (String) -> Unit = {},
    onSetProvider: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onDeposit: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val errorText = state.errorText
    val depositChain = state.depositChain
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

            FormCard {
                Text(
                    text = state.depositMessage.asString(),
                    color = Theme.colors.neutral100,
                    style = Theme.menlo.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(
                            horizontal = 12.dp, vertical = 16.dp
                        ),
                )
            }

            val withdrawPoolTitle = stringResource(R.string.deposit_form_remove_from_pool)
            val depositPoolTitle = stringResource(R.string.deposit_form_add_to_pool)

            FormSelection(
                selected = state.depositOption,
                options = state.depositOptions,
                mapTypeToString = {
                    when (it) {
                        DepositOption.WithdrawPool -> withdrawPoolTitle
                        DepositOption.DepositPool -> depositPoolTitle
                        else -> it.name
                    }
                },
                onSelectOption = onSelectDepositOption,
            )

            when (val depositOption = state.depositOption) {
                DepositOption.DepositPool, DepositOption.WithdrawPool -> {
                    if (depositOption != DepositOption.WithdrawPool) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_amount_title),
                            hint = stringResource(R.string.send_amount_currency_hint),
                            keyboardType = KeyboardType.Number,
                            textFieldState = tokenAmountFieldState,
                            onLostFocus = onTokenAmountLostFocus,
                            error = state.tokenAmountError,
                        )
                    } else {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_percentage_to_remove_title),
                            hint = "0",
                            keyboardType = KeyboardType.Number,
                            textFieldState = basisPointsFieldState,
                            onLostFocus = onBasisPointsLostFocus,
                            error = state.basisPointsError,
                        )
                    }
                }

                else -> {
                    if (depositOption != DepositOption.Leave && depositChain == DepositChain.Thor ||
                        depositOption == DepositOption.Custom && depositChain == DepositChain.Maya
                    ) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_amount_title),
                            hint = stringResource(R.string.send_amount_currency_hint),
                            keyboardType = KeyboardType.Number,
                            textFieldState = tokenAmountFieldState,
                            onLostFocus = onTokenAmountLostFocus,
                            error = state.tokenAmountError,
                        )
                    }

                    if (depositOption != DepositOption.Custom) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_node_address_title),
                            hint = stringResource(R.string.deposit_form_node_address_title),
                            keyboardType = KeyboardType.Text,
                            textFieldState = nodeAddressFieldState,
                            onLostFocus = onNodeAddressLostFocus,
                            error = state.nodeAddressError,
                        ) {
                            val clipboard = LocalClipboardManager.current

                            UiIcon(
                                drawableResId = R.drawable.ic_paste,
                                size = 20.dp,
                                onClick = {
                                    clipboard.getText()
                                        ?.toString()
                                        ?.let(onSetNodeAddress)
                                }
                            )

                            UiSpacer(size = 8.dp)
//                UiIcon(
//                    drawableResId = R.drawable.camera,
//                    size = 20.dp,
//                    onClick = onScan,
//                )
                        }
                    }

                    if (depositOption in listOf(DepositOption.Bond, DepositOption.Unbond)) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_provider_title),
                            hint = stringResource(R.string.deposit_form_provider_hint),
                            keyboardType = KeyboardType.Text,
                            textFieldState = providerFieldState,
                            onLostFocus = onProviderLostFocus,
                            error = state.providerError,
                        ) {
                            val clipboard = LocalClipboardManager.current

                            UiIcon(
                                drawableResId = R.drawable.ic_paste,
                                size = 20.dp,
                                onClick = {
                                    clipboard.getText()
                                        ?.toString()
                                        ?.let(onSetProvider)
                                }
                            )

                            UiSpacer(size = 8.dp)
//                UiIcon(
//                    drawableResId = R.drawable.camera,
//                    size = 20.dp,
//                    onClick = onScan,
//                )
                        }

                        if (depositChain == DepositChain.Maya) {

                            FormTextFieldCard(
                                title = stringResource(R.string.deposit_form_screen_assets),
                                hint = "Enter Assets",
                                keyboardType = KeyboardType.Text,
                                textFieldState = assetsFieldState,
                                onLostFocus = onAssetsLostFocus,
                                error = state.assetsError,
                            )

                            FormTextFieldCard(
                                title = stringResource(R.string.deposit_form_screen_lpunits),
                                hint = "LP units",
                                keyboardType = KeyboardType.Number,
                                textFieldState = lpUnitsFieldState,
                                onLostFocus = onLpUnitsLostFocus,
                                error = state.lpUnitsError,
                            )
                        }
                    }

                    if (depositOption == DepositOption.Bond && depositChain == DepositChain.Thor) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_operator_fee_title),
                            hint = "0.0",
                            keyboardType = KeyboardType.Number,
                            textFieldState = operatorFeeFieldState,
                            onLostFocus = onOperatorFeeLostFocus,
                            error = state.operatorFeeError,
                        )
                    }

                    if (depositOption == DepositOption.Custom) {
                        FormTextFieldCard(
                            title = stringResource(R.string.deposit_form_custom_memo_title),
                            hint = stringResource(R.string.deposit_form_custom_memo_title),
                            keyboardType = KeyboardType.Text,
                            textFieldState = customMemoFieldState,
                            onLostFocus = onCustomMemoLostFocus,
                            error = state.customMemoError,
                        )
                    }
                }
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
            onClick = {
                focusManager.clearFocus()
                onDeposit()
            },
        )
    }

}

@Preview
@Composable
internal fun DepositFormScreenPreview() {
    DepositFormScreen(
        state = DepositFormUiModel(),
        tokenAmountFieldState = TextFieldState(),
        nodeAddressFieldState = TextFieldState(),
        providerFieldState = TextFieldState(),
        operatorFeeFieldState = TextFieldState(),
        customMemoFieldState = TextFieldState(),
        basisPointsFieldState = TextFieldState(),
        lpUnitsFieldState = TextFieldState(),
        assetsFieldState = TextFieldState(),
    )
}