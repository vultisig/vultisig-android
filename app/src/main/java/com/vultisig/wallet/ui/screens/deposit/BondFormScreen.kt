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
import androidx.compose.ui.text.input.ImeAction
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
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.components.library.form.TextFieldValidator
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.vsStyledBackground
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
            else -> stringResource(R.string.bond)
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
        lpUnitsFieldState = model.lpUnitsFieldState,
        onNodeAddressLostFocus = model::validateNodeAddress,
        onProviderLostFocus = model::validateProvider,
        onOperatorFeeLostFocus = model::validateOperatorFee,
        onTokenAmountLostFocus = model::validateTokenAmount,
        onLpUnitsLostFocus = model::validateLpUnits,
        onSetNodeAddress = model::setNodeAddress,
        onSetProvider = model::setProvider,
        onSelectBondAsset = model::selectBondAsset,
        onSetMaxLpUnits = model::setMaxLpUnits,
        onScan = model::scan,
        onAddressBookClick = model::openAddressBook,
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
    lpUnitsFieldState: TextFieldState,
    onNodeAddressLostFocus: () -> Unit = {},
    onProviderLostFocus: () -> Unit = {},
    onOperatorFeeLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onLpUnitsLostFocus: () -> Unit = {},
    onSetNodeAddress: (String) -> Unit = {},
    onSetProvider: (String) -> Unit = {},
    onSelectBondAsset: (String) -> Unit = {},
    onSetMaxLpUnits: () -> Unit = {},
    onScan: () -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onDeposit: () -> Unit = {},
    initialAddressExpanded: Boolean = true,
    initialAssetExpanded: Boolean = false,
) {
    val focusManager = LocalFocusManager.current

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
            MayaBondFormContent(
                state = state,
                nodeAddressFieldState = nodeAddressFieldState,
                lpUnitsFieldState = lpUnitsFieldState,
                onNodeAddressLostFocus = onNodeAddressLostFocus,
                onLpUnitsLostFocus = onLpUnitsLostFocus,
                onSetNodeAddress = onSetNodeAddress,
                onSelectBondAsset = onSelectBondAsset,
                onSetMaxLpUnits = onSetMaxLpUnits,
                onScan = onScan,
                onAddressBookClick = onAddressBookClick,
                initialAddressExpanded = initialAddressExpanded,
                initialAssetExpanded = initialAssetExpanded,
            )

            UiSpacer(size = 80.dp)
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            onClick = {
                focusManager.clearFocus()
                onDeposit()
            },
            state =
                if (state.isLoading || state.isCheckingWhitelist || state.nodeAddressError != null)
                    VsButtonState.Disabled
                else VsButtonState.Enabled,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(all = 16.dp),
        )
    }
}

private val cardShape = RoundedCornerShape(12.dp)
private val inputShape = RoundedCornerShape(12.dp)

private fun String.toDisplayAsset(): String =
    substringBefore("-").substringAfterLast("/").substringAfterLast(".")

private fun String.toDisplayChain(): String? {
    val withoutSuffix = substringBefore("-")
    return if ('.' in withoutSuffix) withoutSuffix.substringBefore(".") else null
}

@Composable
private fun MayaBondFormContent(
    state: DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    lpUnitsFieldState: TextFieldState,
    onNodeAddressLostFocus: () -> Unit,
    onLpUnitsLostFocus: () -> Unit,
    onSetNodeAddress: (String) -> Unit,
    onSelectBondAsset: (String) -> Unit,
    onSetMaxLpUnits: () -> Unit,
    onScan: () -> Unit,
    onAddressBookClick: () -> Unit,
    initialAddressExpanded: Boolean = true,
    initialAssetExpanded: Boolean = false,
) {
    var isAddressExpanded by remember { mutableStateOf(initialAddressExpanded) }
    var isAddressFocused by remember { mutableStateOf(false) }
    var isAssetExpanded by remember { mutableStateOf(initialAssetExpanded) }
    var isBondAssetListOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.nodeAddressError) {
        if (state.nodeAddressError != null) {
            isAddressExpanded = true
        }
    }

    // Address card
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary, cardShape)
                .border(1.dp, Theme.v2.colors.border.normal, cardShape)
                .padding(horizontal = 12.dp, vertical = 16.dp)
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
            )
        }

        AnimatedVisibility(visible = isAddressExpanded) {
            Column() {
                UiSpacer(16.dp)
                UiHorizontalDivider()
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.deposit_form_node_address_title),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
                UiSpacer(8.dp)
                VsTextInputField(
                    hint = stringResource(R.string.send_to_address_hint),
                    keyboardType = KeyboardType.Text,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                    textFieldState = nodeAddressFieldState,
                    onFocusChanged = { isFocused ->
                        if (isFocused) {
                            isAddressFocused = true
                        } else if (isAddressFocused) {
                            isAddressFocused = false
                            onNodeAddressLostFocus()
                            if (state.nodeAddressError == null) {
                                isAddressExpanded = false
                            }
                        }
                    },
                    innerState =
                        if (state.nodeAddressError != null) VsTextInputFieldInnerState.Error
                        else VsTextInputFieldInnerState.Default,
                    footNote = state.nodeAddressError?.asString(),
                    modifier = Modifier.fillMaxWidth(),
                )
                UiSpacer(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasteIcon(
                        modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
                        onPaste = onSetNodeAddress,
                    )
                    UiIcon(
                        drawableResId = R.drawable.camera,
                        size = 20.dp,
                        modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
                        onClick = onScan,
                    )
                    UiIcon(
                        drawableResId = R.drawable.ic_bookmark,
                        size = 20.dp,
                        modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
                        onClick = onAddressBookClick,
                    )
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
                .padding(12.dp),
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
                UiSpacer(size = 12.dp)
                TokenLogo(
                    logo = getCoinLogo(state.selectedBondAsset.toDisplayAsset().lowercase()),
                    title = state.selectedBondAsset.toDisplayAsset(),
                    modifier = Modifier.size(16.dp),
                    errorLogoModifier = Modifier.size(16.dp),
                )
                UiSpacer(size = 4.dp)
                Text(
                    text = state.selectedBondAsset.toDisplayAsset(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
                UiSpacer(weight = 1f)
                UiIcon(
                    drawableResId = R.drawable.check_3,
                    size = 16.dp,
                    tint = Theme.v2.colors.alerts.success,
                )
                UiSpacer(size = 12.dp)
                UiIcon(
                    drawableResId = R.drawable.ic_edit_pencil,
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
                UiGradientHorizontalDivider()
                UiSpacer(size = 16.dp)
                if (state.bondableAssets.isNotEmpty()) {
                    TextFieldValidator(errorText = state.assetsError) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Selected chip — always visible; click to open the full list
                            val displayAsset =
                                state.selectedBondAsset.ifEmpty { state.bondableAssets.first() }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.clickOnce {
                                            isBondAssetListOpen = !isBondAssetListOpen
                                        }
                                        .background(
                                            color = Theme.v2.colors.backgrounds.surface1,
                                            shape = RoundedCornerShape(99.dp),
                                        )
                                        .padding(
                                            start = 6.dp,
                                            end = 12.dp,
                                            top = 6.dp,
                                            bottom = 6.dp,
                                        ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TokenLogo(
                                        logo =
                                            getCoinLogo(displayAsset.toDisplayAsset().lowercase()),
                                        title = displayAsset.toDisplayAsset(),
                                        modifier = Modifier.size(36.dp),
                                        errorLogoModifier = Modifier.size(36.dp),
                                    )
                                    UiSpacer(8.dp)
                                    Column {
                                        Text(
                                            text = displayAsset.toDisplayAsset(),
                                            style = Theme.brockmann.supplementary.caption,
                                            color = Theme.v2.colors.text.primary,
                                        )
                                        Text(
                                            text =
                                                displayAsset.toDisplayChain()
                                                    ?: stringResource(R.string.swap_form_native),
                                            style = Theme.brockmann.supplementary.captionSmall,
                                            color = Theme.v2.colors.text.tertiary,
                                        )
                                    }
                                }
                                UiSpacer(4.dp)
                                UiIcon(
                                    drawableResId =
                                        if (isBondAssetListOpen) R.drawable.ic_caret_down
                                        else R.drawable.ic_chevron_right_small,
                                    size = 20.dp,
                                    tint = Theme.v2.colors.text.primary,
                                )
                            }

                            // Full list — visible only when chip is tapped
                            AnimatedVisibility(visible = isBondAssetListOpen) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.bondableAssets.forEach { asset ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier =
                                                Modifier.clickOnce {
                                                        onSelectBondAsset(asset)
                                                        isBondAssetListOpen = false
                                                        isAssetExpanded = false
                                                    }
                                                    .background(
                                                        color =
                                                            Theme.v2.colors.backgrounds.secondary,
                                                        shape = RoundedCornerShape(99.dp),
                                                    )
                                                    .padding(
                                                        start = 6.dp,
                                                        end = 12.dp,
                                                        top = 6.dp,
                                                        bottom = 6.dp,
                                                    ),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                TokenLogo(
                                                    logo =
                                                        getCoinLogo(
                                                            asset.toDisplayAsset().lowercase()
                                                        ),
                                                    title = asset.toDisplayAsset(),
                                                    modifier = Modifier.size(36.dp),
                                                    errorLogoModifier = Modifier.size(36.dp),
                                                )
                                                Column {
                                                    Text(
                                                        text = asset.toDisplayAsset(),
                                                        style =
                                                            Theme.brockmann.supplementary.caption,
                                                        color = Theme.v2.colors.text.primary,
                                                    )
                                                    Text(
                                                        text =
                                                            asset.toDisplayChain()
                                                                ?: stringResource(
                                                                    R.string.swap_form_native
                                                                ),
                                                        style =
                                                            Theme.brockmann.supplementary
                                                                .captionSmall,
                                                        color = Theme.v2.colors.text.tertiary,
                                                    )
                                                }
                                            }
                                            UiIcon(
                                                drawableResId = R.drawable.ic_chevron_right_small,
                                                size = 20.dp,
                                                tint = Theme.v2.colors.text.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TextFieldValidator(errorText = state.lpUnitsError) {
                    Column {
                        UiSpacer(size = 16.dp)
                        Text(
                            text = stringResource(R.string.deposit_form_screen_lpunits),
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.tertiary,
                        )
                        UiSpacer(size = 8.dp)
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
                                hint = "0",
                                keyboardType = KeyboardType.Number,
                                textFieldState = lpUnitsFieldState,
                                textStyle = Theme.brockmann.body.s.medium,
                                onLostFocus = {
                                    onLpUnitsLostFocus()
                                    isAssetExpanded = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        UiSpacer(size = 8.dp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.deposit_form_available_lp_units),
                                style = Theme.brockmann.supplementary.footnote,
                                color = Theme.v2.colors.text.tertiary,
                            )
                            if (state.availableLpUnits != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = state.availableLpUnits,
                                        style = Theme.brockmann.supplementary.footnote,
                                        color = Theme.v2.colors.text.tertiary,
                                    )
                                    Text(
                                        text = stringResource(R.string.send_screen_max).lowercase(),
                                        style = Theme.brockmann.supplementary.footnote,
                                        color = Theme.v2.colors.primary.accent4,
                                        modifier = Modifier.clickOnce { onSetMaxLpUnits() },
                                    )
                                }
                            }
                        }
                        val lpText = lpUnitsFieldState.text.toString()
                        val estimatedBondValue =
                            remember(
                                lpText,
                                state.selectedPoolTotalLpUnits,
                                state.selectedPoolCacaoDepth,
                            ) {
                                val lpLong = lpText.toLongOrNull() ?: 0L
                                if (
                                    lpLong > 0L &&
                                        state.selectedPoolTotalLpUnits > 0L &&
                                        state.selectedPoolCacaoDepth > 0L
                                ) {
                                    val ratio =
                                        lpLong
                                            .toBigDecimal()
                                            .divide(
                                                state.selectedPoolTotalLpUnits.toBigDecimal(),
                                                10,
                                                java.math.RoundingMode.HALF_UP,
                                            )
                                    val cacao =
                                        ratio
                                            .multiply(state.selectedPoolCacaoDepth.toBigDecimal())
                                            .divide(
                                                java.math.BigDecimal("10000000000"),
                                                4,
                                                java.math.RoundingMode.HALF_UP,
                                            )
                                    "${cacao.stripTrailingZeros().toPlainString()} CACAO"
                                } else null
                            }
                        if (estimatedBondValue != null) {
                            UiSpacer(size = 8.dp)
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text =
                                        stringResource(R.string.deposit_form_estimated_bond_value),
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.tertiary,
                                )
                                Text(
                                    text = estimatedBondValue,
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02122B)
@Composable
private fun BondFormContentMayaAssetExpandedPreview() {
    OnBoardingComposeTheme {
        BondFormContent(
            state =
                DepositFormUiModel(
                    depositChain = Chain.MayaChain,
                    bondableAssets = listOf("CACAO", "RUNE"),
                    selectedBondAsset = "CACAO",
                ),
            nodeAddressFieldState = TextFieldState("maya1abctupwgjwn397w3dx9fqmqgzr"),
            initialAddressExpanded = false,
            initialAssetExpanded = true,
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState("1000"),
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
            initialAddressExpanded = true,
            providerFieldState = TextFieldState(),
            operatorFeeFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            lpUnitsFieldState = TextFieldState("1000"),
        )
    }
}
