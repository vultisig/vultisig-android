package com.vultisig.wallet.ui.screens.deposit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun BondFormScreen(navController: NavController, vaultId: String, chainId: String) {
    val bondNavHostController = rememberNavController()
    val context = LocalContext.current
    val keysignShareViewModel: KeysignShareViewModel = hiltViewModel(context as MainActivity)

    val depositViewModel: com.vultisig.wallet.ui.models.deposit.DepositViewModel = hiltViewModel()
    val isKeysignFinished by depositViewModel.isKeysignFinished.collectAsState()

    LaunchedEffect(Unit) {
        depositViewModel.dst.collect { bondNavHostController.route(it.dst.route, it.opts) }
    }

    val navBackStackEntry by bondNavHostController.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route

    val shouldUseMainNavigator = route == SendDst.Send.route
    val topBarNavController = if (shouldUseMainNavigator) navController else bondNavHostController

    val title =
        when (route) {
            SendDst.VerifyTransaction.staticRoute ->
                stringResource(R.string.verify_transaction_screen_title)
            else -> stringResource(R.string.bond_to_node)
        }

    val qrAddress by depositViewModel.addressProvider.address.collectAsState()
    val qr = qrAddress.takeIf { it.isNotEmpty() }

    V2Scaffold(
        title = title,
        onBackClick =
            if (!isKeysignFinished) {
                { topBarNavController.popBackStack() }
            } else null,
        rightIcon = qr?.let { R.drawable.qr_share },
        onRightIconClick = qr?.let { { keysignShareViewModel.shareQRCode(context) } } ?: {},
    ) {
        NavHost(
            navController = bondNavHostController,
            startDestination = SendDst.Send.route,
            enterTransition = slideInFromEndEnterTransition(),
            exitTransition = slideOutToStartExitTransition(),
            popEnterTransition = slideInFromStartEnterTransition(),
            popExitTransition = slideOutToEndExitTransition(),
        ) {
            composable(route = SendDst.Send.route) {
                BondFormContent(vaultId = vaultId, chainId = chainId)
            }
            composable(
                route = SendDst.VerifyTransaction.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                VerifyDepositScreen()
            }
        }
    }
}

@Composable
private fun BondFormContent(
    model: DepositFormViewModel = hiltViewModel(),
    vaultId: String,
    chainId: String,
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) { model.loadData(vaultId, chainId, DepositOption.Bond.name, null) }

    BondFormContent(
        state = state,
        nodeAddressFieldState = model.nodeAddressFieldState,
        providerFieldState = model.providerFieldState,
        operatorFeeFieldState = model.operatorFeeFieldState,
        tokenAmountFieldState = model.tokenAmountFieldState,
        assetsFieldState = model.assetsFieldState,
        lpUnitsFieldState = model.lpUnitsFieldState,
        onNodeAddressLostFocus = model::validateNodeAddress,
        onProviderLostFocus = model::validateProvider,
        onOperatorFeeLostFocus = model::validateOperatorFee,
        onTokenAmountLostFocus = model::validateTokenAmount,
        onAssetsLostFocus = model::validateAssets,
        onLpUnitsLostFocus = model::validateLpUnits,
        onSetNodeAddress = model::setNodeAddress,
        onSetProvider = model::setProvider,
        onSelectBondAsset = model::selectBondAsset,
        onDismissError = model::dismissError,
        onDeposit = model::deposit,
    )
}

@Composable
internal fun BondFormContent(
    state: DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    providerFieldState: TextFieldState,
    operatorFeeFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    assetsFieldState: TextFieldState,
    lpUnitsFieldState: TextFieldState,
    onNodeAddressLostFocus: () -> Unit = {},
    onProviderLostFocus: () -> Unit = {},
    onOperatorFeeLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onAssetsLostFocus: () -> Unit = {},
    onLpUnitsLostFocus: () -> Unit = {},
    onSetNodeAddress: (String) -> Unit = {},
    onSetProvider: (String) -> Unit = {},
    onSelectBondAsset: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
    onDeposit: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val depositChain = state.depositChain

    if (state.errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = state.errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(all = 16.dp).verticalScroll(rememberScrollState()),
        ) {
            // Amount — THORChain bond requires an amount
            if (depositChain == Chain.ThorChain) {
                FormTextFieldCard(
                    title =
                        stringResource(
                            R.string.deposit_form_amount_title,
                            state.balance.asString(),
                        ),
                    hint = stringResource(R.string.send_amount_currency_hint),
                    keyboardType = KeyboardType.Number,
                    textFieldState = tokenAmountFieldState,
                    onLostFocus = onTokenAmountLostFocus,
                    error = state.tokenAmountError,
                )
            }

            // Node address
            FormTextFieldCard(
                title = stringResource(R.string.deposit_form_node_address_title),
                hint = stringResource(R.string.deposit_form_node_address_title),
                keyboardType = KeyboardType.Text,
                textFieldState = nodeAddressFieldState,
                onLostFocus = onNodeAddressLostFocus,
                error = state.nodeAddressError,
            ) {
                PasteIcon(onPaste = onSetNodeAddress)
                UiSpacer(size = 8.dp)
            }

            // Provider address
            FormTextFieldCard(
                title = stringResource(R.string.deposit_form_provider_title),
                hint = stringResource(R.string.deposit_form_provider_hint),
                keyboardType = KeyboardType.Text,
                textFieldState = providerFieldState,
                onLostFocus = onProviderLostFocus,
                error = state.providerError,
            ) {
                PasteIcon(onPaste = onSetProvider)
                UiSpacer(size = 8.dp)
            }

            // MayaChain: asset selection + LP units
            if (depositChain == Chain.MayaChain) {
                if (state.bondableAssets.isNotEmpty()) {
                    FormSelection(
                        selected = state.selectedBondAsset,
                        options = state.bondableAssets,
                        onSelectOption = onSelectBondAsset,
                        mapTypeToString = { it },
                    )
                } else {
                    FormTextFieldCard(
                        title = stringResource(R.string.deposit_form_screen_assets),
                        hint = stringResource(R.string.deposit_form_enter_asset_hint),
                        keyboardType = KeyboardType.Text,
                        textFieldState = assetsFieldState,
                        onLostFocus = onAssetsLostFocus,
                        error = state.assetsError,
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.deposit_form_screen_lpunits),
                    hint = "LP units",
                    keyboardType = KeyboardType.Number,
                    textFieldState = lpUnitsFieldState,
                    onLostFocus = onLpUnitsLostFocus,
                    error = state.lpUnitsError,
                )
            }

            // THORChain: operator fee
            if (depositChain == Chain.ThorChain) {
                FormTextFieldCard(
                    title = stringResource(R.string.deposit_form_operator_fee_title),
                    hint = "0.0",
                    keyboardType = KeyboardType.Number,
                    textFieldState = operatorFeeFieldState,
                    onLostFocus = onOperatorFeeLostFocus,
                    error = state.operatorFeeError,
                )
            }

            UiSpacer(size = 80.dp)
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            onClick = {
                focusManager.clearFocus()
                onDeposit()
            },
            state = if (state.isLoading) VsButtonState.Disabled else VsButtonState.Enabled,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(all = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun BondFormContentPreview() {
    OnBoardingComposeTheme {
        BondFormContent(
            state = DepositFormUiModel(depositChain = Chain.ThorChain),
            nodeAddressFieldState = TextFieldState(),
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            assetsFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState(),
        )
    }
}
