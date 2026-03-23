package com.vultisig.wallet.ui.screens.deposit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.components.library.form.FormTextField
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.FormTitleCollapsibleTextField
import com.vultisig.wallet.ui.components.library.form.TextFieldValidator
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
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
        onScan = model::scan,
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
    onScan: () -> Unit = {},
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
            if (depositChain == Chain.MayaChain) {
                MayaBondFormContent(
                    state = state,
                    nodeAddressFieldState = nodeAddressFieldState,
                    assetsFieldState = assetsFieldState,
                    lpUnitsFieldState = lpUnitsFieldState,
                    onNodeAddressLostFocus = onNodeAddressLostFocus,
                    onAssetsLostFocus = onAssetsLostFocus,
                    onLpUnitsLostFocus = onLpUnitsLostFocus,
                    onSetNodeAddress = onSetNodeAddress,
                    onSelectBondAsset = onSelectBondAsset,
                    onScan = onScan,
                )
            } else {
                ThorchainBondFormContent(
                    state = state,
                    nodeAddressFieldState = nodeAddressFieldState,
                    providerFieldState = providerFieldState,
                    operatorFeeFieldState = operatorFeeFieldState,
                    tokenAmountFieldState = tokenAmountFieldState,
                    onNodeAddressLostFocus = onNodeAddressLostFocus,
                    onProviderLostFocus = onProviderLostFocus,
                    onOperatorFeeLostFocus = onOperatorFeeLostFocus,
                    onTokenAmountLostFocus = onTokenAmountLostFocus,
                    onSetNodeAddress = onSetNodeAddress,
                    onSetProvider = onSetProvider,
                    onScan = onScan,
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

private val cardShape = RoundedCornerShape(12.dp)
private val inputShape = RoundedCornerShape(12.dp)

@Composable
private fun MayaBondFormContent(
    state: DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    assetsFieldState: TextFieldState,
    lpUnitsFieldState: TextFieldState,
    onNodeAddressLostFocus: () -> Unit,
    onAssetsLostFocus: () -> Unit,
    onLpUnitsLostFocus: () -> Unit,
    onSetNodeAddress: (String) -> Unit,
    onSelectBondAsset: (String) -> Unit,
    onScan: () -> Unit,
) {
    var isAddressExpanded by remember { mutableStateOf(true) }
    var isAssetExpanded by remember { mutableStateOf(state.selectedBondAsset.isEmpty()) }

    // Address card
    TextFieldValidator(errorText = state.nodeAddressError) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.primary, cardShape)
                    .border(1.dp, Theme.v2.colors.border.normal, cardShape)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth().clickOnce {
                        isAddressExpanded = !isAddressExpanded
                        if (isAddressExpanded) isAssetExpanded = false
                    },
            ) {
                Text(
                    text = stringResource(R.string.deposit_form_address_card_title),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(visible = isAddressExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiHorizontalDivider()
                    Text(
                        text = stringResource(R.string.deposit_form_node_address_title),
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.tertiary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth()
                                .background(Theme.v2.colors.backgrounds.secondary, inputShape)
                                .border(
                                    1.dp,
                                    Theme.v2.colors.variables.bordersExtraLight,
                                    inputShape,
                                )
                                .padding(16.dp),
                    ) {
                        BasicFormTextField(
                            hint = stringResource(R.string.deposit_form_node_address_title),
                            keyboardType = KeyboardType.Text,
                            textFieldState = nodeAddressFieldState,
                            onLostFocus = {
                                onNodeAddressLostFocus()
                                isAddressExpanded = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        UiSpacer(size = 8.dp)
                        UiIcon(
                            drawableResId = R.drawable.camera,
                            size = 20.dp,
                            modifier = Modifier.clickOnce { onScan() },
                        )
                        UiSpacer(size = 8.dp)
                        PasteIcon(onPaste = onSetNodeAddress)
                    }
                }
            }
        }
    }

    // Asset card
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary, cardShape)
                .border(1.dp, Theme.v2.colors.border.normal, cardShape)
                .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth().clickOnce {
                    isAssetExpanded = !isAssetExpanded
                    if (isAssetExpanded) isAddressExpanded = false
                },
        ) {
            Text(
                text =
                    stringResource(
                        if (isAssetExpanded) R.string.deposit_form_screen_asset_selection
                        else R.string.deposit_form_screen_assets
                    ),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (!isAssetExpanded && state.selectedBondAsset.isNotEmpty()) {
                UiSpacer(size = 4.dp)
                TokenLogo(
                    logo = getCoinLogo(state.selectedBondAsset.lowercase()),
                    title = state.selectedBondAsset,
                    modifier = Modifier.size(16.dp),
                    errorLogoModifier = Modifier.size(16.dp),
                )
                UiSpacer(size = 4.dp)
                Text(
                    text = state.selectedBondAsset,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
                UiSpacer(weight = 1f)
                UiIcon(
                    drawableResId = R.drawable.ic_check,
                    size = 16.dp,
                    tint = Theme.v2.colors.alerts.success,
                )
                UiSpacer(size = 8.dp)
                UiIcon(
                    drawableResId = R.drawable.pencil,
                    size = 16.dp,
                    modifier =
                        Modifier.clickOnce {
                            isAssetExpanded = true
                            isAddressExpanded = false
                        },
                )
            } else {
                UiSpacer(weight = 1f)
            }
        }
        AnimatedVisibility(visible = isAssetExpanded) {
            Column {
                UiHorizontalDivider()
                UiSpacer(size = 8.dp)
                if (state.bondableAssets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.bondableAssets.forEach { asset ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.clickOnce {
                                            onSelectBondAsset(asset)
                                            isAssetExpanded = false
                                        }
                                        .background(
                                            color = Theme.v2.colors.backgrounds.tertiary_2,
                                            shape = RoundedCornerShape(99.dp),
                                        )
                                        .padding(all = 6.dp),
                            ) {
                                TokenLogo(
                                    logo = getCoinLogo(asset.lowercase()),
                                    title = asset,
                                    modifier = Modifier.size(36.dp),
                                    errorLogoModifier = Modifier.size(36.dp),
                                )
                                UiSpacer(size = 8.dp)
                                Column {
                                    Text(
                                        text = asset,
                                        style = Theme.brockmann.supplementary.caption,
                                        color = Theme.v2.colors.text.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.swap_form_native),
                                        style = Theme.brockmann.supplementary.captionSmall,
                                        color = Theme.v2.colors.text.tertiary,
                                    )
                                }
                                UiSpacer(size = 4.dp)
                                UiIcon(
                                    drawableResId = R.drawable.ic_chevron_right_small,
                                    size = 20.dp,
                                    tint = Theme.v2.colors.text.primary,
                                )
                                UiSpacer(size = 6.dp)
                            }
                        }
                    }
                } else {
                    TextFieldValidator(errorText = state.assetsError) {
                        FormTextField(
                            hint = stringResource(R.string.deposit_form_enter_asset_hint),
                            keyboardType = KeyboardType.Text,
                            textFieldState = assetsFieldState,
                            onLostFocus = {
                                onAssetsLostFocus()
                                isAssetExpanded = false
                            },
                        )
                    }
                }
                UiHorizontalDivider()
                TextFieldValidator(errorText = state.lpUnitsError) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.deposit_form_screen_lpunits),
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.tertiary,
                        )
                        UiSpacer(size = 4.dp)
                        FormTextField(
                            hint = "0",
                            keyboardType = KeyboardType.Number,
                            textFieldState = lpUnitsFieldState,
                            onLostFocus = {
                                onLpUnitsLostFocus()
                                isAssetExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThorchainBondFormContent(
    state: DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    providerFieldState: TextFieldState,
    operatorFeeFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    onNodeAddressLostFocus: () -> Unit,
    onProviderLostFocus: () -> Unit,
    onOperatorFeeLostFocus: () -> Unit,
    onTokenAmountLostFocus: () -> Unit,
    onSetNodeAddress: (String) -> Unit,
    onSetProvider: (String) -> Unit,
    onScan: () -> Unit,
) {
    // Node address — first
    FormTextFieldCard(
        title = stringResource(R.string.deposit_form_node_address_title),
        hint = stringResource(R.string.deposit_form_node_address_title),
        keyboardType = KeyboardType.Text,
        textFieldState = nodeAddressFieldState,
        onLostFocus = onNodeAddressLostFocus,
        error = state.nodeAddressError,
    ) {
        UiIcon(
            drawableResId = R.drawable.camera,
            size = 20.dp,
            modifier = Modifier.clickOnce { onScan() },
        )
        UiSpacer(size = 8.dp)
        PasteIcon(onPaste = onSetNodeAddress)
        UiSpacer(size = 8.dp)
    }

    // Amount
    FormTextFieldCard(
        title = stringResource(R.string.deposit_form_amount_title, state.balance.asString()),
        hint = stringResource(R.string.send_amount_currency_hint),
        keyboardType = KeyboardType.Number,
        textFieldState = tokenAmountFieldState,
        onLostFocus = onTokenAmountLostFocus,
        error = state.tokenAmountError,
    )

    // Operator fee
    FormTextFieldCard(
        title = stringResource(R.string.deposit_form_operator_fee_title),
        hint = "0.0",
        keyboardType = KeyboardType.Number,
        textFieldState = operatorFeeFieldState,
        onLostFocus = onOperatorFeeLostFocus,
        error = state.operatorFeeError,
    )

    // Provider — collapsible
    FormTitleCollapsibleTextField(
        title = stringResource(R.string.deposit_form_provider_title),
        hint = stringResource(R.string.deposit_form_provider_hint),
        keyboardType = KeyboardType.Text,
        textFieldState = providerFieldState,
        onLostFocus = onProviderLostFocus,
    ) {
        PasteIcon(onPaste = onSetProvider)
        UiSpacer(size = 8.dp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun BondFormContentThorPreview() {
    OnBoardingComposeTheme {
        BondFormContent(
            state = DepositFormUiModel(depositChain = Chain.ThorChain),
            nodeAddressFieldState = TextFieldState("thor1mtqtupwgjwn397w3dx9fqmqgzr"),
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            assetsFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun BondFormContentMayaExpandedPreview() {
    OnBoardingComposeTheme {
        BondFormContent(
            state =
                DepositFormUiModel(
                    depositChain = Chain.MayaChain,
                    bondableAssets = listOf("CACAO", "RUNE"),
                    selectedBondAsset = "",
                ),
            nodeAddressFieldState = TextFieldState(""),
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            assetsFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun BondFormContentMayaCollapsedPreview() {
    OnBoardingComposeTheme {
        BondFormContent(
            state =
                DepositFormUiModel(
                    depositChain = Chain.MayaChain,
                    bondableAssets = listOf("CACAO", "RUNE"),
                    selectedBondAsset = "CACAO",
                ),
            nodeAddressFieldState = TextFieldState("maya1abctupwgjwn397w3dx9fqmqgzr"),
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            assetsFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState("1000"),
        )
    }
}
