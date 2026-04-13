@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.v2.fastselection.contentWithFastSelection
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.send.AddressBookType
import com.vultisig.wallet.ui.models.send.AmountFraction
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

internal fun NavGraphBuilder.sendScreen(navController: NavHostController) {
    contentWithFastSelection<Route.Send.SendMain, Route.Send>(navController = navController) {
        onNetworkDragStart,
        onNetworkDrag,
        onNetworkDragEnd ->
        val viewModel: SendFormViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsState()

        val addressFocusRequester = remember { FocusRequester() }
        val amountFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            viewModel.focusFieldFlow.collect { field ->
                when (field) {
                    SendFocusField.ADDRESS -> addressFocusRequester.requestFocus()
                    SendFocusField.AMOUNT -> amountFocusRequester.requestFocus()
                }
            }
        }

        SendFormScreen(
            state = state,
            addressFieldState = viewModel.addressFieldState,
            addressFocusRequester = addressFocusRequester,
            amountFocusRequester = amountFocusRequester,
            tokenAmountFieldState = viewModel.tokenAmountFieldState,
            fiatAmountFieldState = viewModel.fiatAmountFieldState,
            memoFieldState = viewModel.memoFieldState,
            onDstAddressLostFocus = { /* no-op */ },
            onTokenAmountLostFocus = viewModel::validateTokenAmount,
            onDismissError = viewModel::dismissError,
            onSelectNetworkRequest = viewModel::selectNetwork,
            onSelectTokenRequest = viewModel::openTokenSelection,
            onSetOutputAddress = viewModel::setOutputAddress,
            onChooseMaxTokenAmount = viewModel::chooseMaxTokenAmount,
            onChoosePercentageAmount = viewModel::choosePercentageAmount,
            onScanDstAddressRequest = viewModel::scanAddress,
            onAddressBookClick = viewModel::openAddressBook,
            onSend = viewModel::onClickContinue,
            onRefreshRequest = viewModel::refreshGasFee,
            onGasSettingsClick = viewModel::openGasSettings,
            onBackClick = viewModel::back,
            onToggleAmountInputType = viewModel::toggleAmountInputType,
            onExpandSection = viewModel::expandSection,
            onNetworkDragStart = onNetworkDragStart,
            onNetworkDrag = onNetworkDrag,
            onNetworkDragEnd = onNetworkDragEnd,
            onNetworkDragCancel = onNetworkDragEnd,
            onNetworkLongPressStarted = viewModel::onNetworkLongPressStarted,
            onAssetDragStart = onNetworkDragStart,
            onAssetDrag = onNetworkDrag,
            onAssetDragEnd = onNetworkDragEnd,
            onAssetDragCancel = onNetworkDragEnd,
            onAssetLongPressStarted = viewModel::openTokenSelectionPopup,
            operatorFeeFieldState = viewModel.operatorFeesBondFieldState,
            providerFieldState = viewModel.providerBondFieldState,
            slippageFieldState = viewModel.slippageFieldState,
            onSetProviderAddressRequest = viewModel::setProviderAddress,
            onScanProviderAddressRequest = viewModel::scanProviderAddress,
            onAddressProviderBookClick = { viewModel.openAddressBook(AddressBookType.PROVIDER) },
            onAutoCompound = { viewModel.onAutoCompound(it) },
        )

        val selectedChain = state.selectedCoin?.model?.address?.chain
        val specific = state.specific

        if (state.showGasSettings && selectedChain != null && specific != null) {
            GasSettingsScreen(
                chain = selectedChain,
                specific = specific,
                onSaveGasSettings = viewModel::saveGasSettings,
                onDismissGasSettings = viewModel::dismissGasSettings,
            )
        }
    }
}

@Composable
private fun SendFormScreen(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    addressFocusRequester: FocusRequester = FocusRequester(),
    amountFocusRequester: FocusRequester = FocusRequester(),
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    memoFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onSelectNetworkRequest: () -> Unit = {},
    onSelectTokenRequest: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onChooseMaxTokenAmount: () -> Unit = {},
    onChoosePercentageAmount: (AmountFraction) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScanDstAddressRequest: () -> Unit = {},
    onSend: () -> Unit = {},
    onAddressProviderBookClick: () -> Unit = {},
    onScanProviderAddressRequest: () -> Unit = {},
    onSetProviderAddressRequest: (String) -> Unit = {},
    onRefreshRequest: () -> Unit = {},
    onGasSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToggleAmountInputType: (Boolean) -> Unit = {},
    onExpandSection: (SendSections) -> Unit = {},
    onNetworkDragStart: (Offset) -> Unit,
    onNetworkDrag: (Offset) -> Unit,
    onNetworkDragEnd: () -> Unit,
    onNetworkDragCancel: () -> Unit,
    onNetworkLongPressStarted: (Offset) -> Unit,
    onAssetDragStart: (Offset) -> Unit,
    onAssetDrag: (Offset) -> Unit,
    onAssetDragEnd: () -> Unit,
    onAssetDragCancel: () -> Unit,
    onAssetLongPressStarted: (Offset) -> Unit,

    // bond fields
    operatorFeeFieldState: TextFieldState,
    providerFieldState: TextFieldState,

    // trade
    slippageFieldState: TextFieldState,

    // autocompound
    onAutoCompound: (Boolean) -> Unit = {},
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

    V2Scaffold(
        title =
            when (state.defiType) {
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.STAKE_STCY -> stringResource(R.string.stake_screen_title)
                DeFiNavActions.UNSTAKE_TCY,
                DeFiNavActions.UNSTAKE_RUJI,
                DeFiNavActions.UNSTAKE_STCY -> stringResource(R.string.unstake_screen_title)
                DeFiNavActions.MINT_YRUNE,
                DeFiNavActions.MINT_YTCY -> stringResource(R.string.mint_screen_title)
                DeFiNavActions.REDEEM_YRUNE,
                DeFiNavActions.REDEEM_YTCY -> stringResource(R.string.redeem_screen_title)
                DeFiNavActions.BOND -> stringResource(R.string.bond_screen_title)
                DeFiNavActions.UNBOND -> stringResource(R.string.unbond_screen_title)
                DeFiNavActions.WITHDRAW_RUJI -> stringResource(R.string.rewards_screen_title)
                DeFiNavActions.WITHDRAW_USDC_CIRCLE -> stringResource(R.string.withdraw)
                else -> stringResource(R.string.send_screen_title)
            },
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (state.isLoading) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = {
                    if (!state.isLoading) {
                        focusManager.clearFocus()
                        onSend()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            )
        },
        content = {
            val pullToRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefreshRequest,
                state = pullToRefreshState,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        color = Theme.v2.colors.primary.accent3,
                        state = pullToRefreshState,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val isCircleMode =
                    state.defiType == DeFiNavActions.DEPOSIT_USDC_CIRCLE ||
                        state.defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        if (isCircleMode) Modifier.fillMaxHeight()
                        else Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SendFormContent(
                        state = state,
                        onExpandSection = onExpandSection,
                        onSelectNetworkRequest = onSelectNetworkRequest,
                        onNetworkDragCancel = onNetworkDragCancel,
                        onNetworkDrag = onNetworkDrag,
                        onNetworkDragStart = onNetworkDragStart,
                        onNetworkDragEnd = onNetworkDragEnd,
                        onNetworkLongPressStarted = onNetworkLongPressStarted,
                        onSelectTokenRequest = onSelectTokenRequest,
                        onAssetDragCancel = onAssetDragCancel,
                        onAssetDrag = onAssetDrag,
                        onAssetDragStart = onAssetDragStart,
                        onAssetDragEnd = onAssetDragEnd,
                        onAssetLongPressStarted = onAssetLongPressStarted,
                        addressFieldState = addressFieldState,
                        addressFocusRequester = addressFocusRequester,
                        amountFocusRequester = amountFocusRequester,
                        onDstAddressLostFocus = onDstAddressLostFocus,
                        onSetOutputAddress = onSetOutputAddress,
                        onScanDstAddressRequest = onScanDstAddressRequest,
                        onAddressBookClick = onAddressBookClick,
                        onGasSettingsClick = onGasSettingsClick,
                        tokenAmountFieldState = tokenAmountFieldState,
                        fiatAmountFieldState = fiatAmountFieldState,
                        focusManager = focusManager,
                        onSend = onSend,
                        onToggleAmountInputType = onToggleAmountInputType,
                        onChoosePercentageAmount = onChoosePercentageAmount,
                        onChooseMaxTokenAmount = onChooseMaxTokenAmount,
                        onTokenAmountLostFocus = onTokenAmountLostFocus,
                        memoFieldState = memoFieldState,

                        // Bond
                        operatorFeeFieldState = operatorFeeFieldState,
                        providerFieldState = providerFieldState,
                        onSetProvider = onSetProviderAddressRequest,
                        onScanProvider = onScanProviderAddressRequest,
                        onProviderBookClick = onAddressProviderBookClick,

                        // trade
                        slippageFieldState = slippageFieldState,
                        onAutoCompoundCheckedChange = onAutoCompound,
                    )
                }
            }
        },
    )
}

@Composable
private fun SendFormContent(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    onSelectNetworkRequest: () -> Unit,
    onNetworkDragCancel: () -> Unit,
    onNetworkDrag: (Offset) -> Unit,
    onNetworkDragStart: (Offset) -> Unit,
    onNetworkDragEnd: () -> Unit,
    onNetworkLongPressStarted: (Offset) -> Unit,
    onSelectTokenRequest: () -> Unit,
    onAssetDragCancel: () -> Unit,
    onAssetDrag: (Offset) -> Unit,
    onAssetDragStart: (Offset) -> Unit,
    onAssetDragEnd: () -> Unit,
    onAssetLongPressStarted: (Offset) -> Unit,
    addressFieldState: TextFieldState,
    addressFocusRequester: FocusRequester,
    amountFocusRequester: FocusRequester,
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
    onGasSettingsClick: () -> Unit,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    focusManager: FocusManager,
    onSend: () -> Unit,
    onToggleAmountInputType: (Boolean) -> Unit,
    onChoosePercentageAmount: (AmountFraction) -> Unit,
    onChooseMaxTokenAmount: () -> Unit,
    onTokenAmountLostFocus: () -> Unit = {},
    memoFieldState: TextFieldState,

    // Bond
    operatorFeeFieldState: TextFieldState,
    providerFieldState: TextFieldState,
    onSetProvider: (String) -> Unit,
    onScanProvider: () -> Unit,
    onProviderBookClick: () -> Unit,

    // trade
    slippageFieldState: TextFieldState,

    // stake tcy
    onAutoCompoundCheckedChange: (Boolean) -> Unit,
) {
    val amountInputs =
        AmountInputs(
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            focusRequester = amountFocusRequester,
            onToggleAmountInputType = onToggleAmountInputType,
            onChoosePercentageAmount = onChoosePercentageAmount,
            onChooseMaxTokenAmount = onChooseMaxTokenAmount,
            onTokenAmountLostFocus = onTokenAmountLostFocus,
        )
    val optionalInputs =
        OptionalInputs(
            memoFieldState = memoFieldState,
            operatorFeeFieldState = operatorFeeFieldState,
            slippageFieldState = slippageFieldState,
            onAutoCompoundCheckedChange = onAutoCompoundCheckedChange,
        )

    // send asset
    if (state.defiType == null) {
        FoldableAssetWidget(
            state = state,
            onExpandSection = onExpandSection,
            onSelectNetworkRequest = onSelectNetworkRequest,
            onNetworkDragCancel = onNetworkDragCancel,
            onNetworkDrag = onNetworkDrag,
            onNetworkDragStart = onNetworkDragStart,
            onNetworkDragEnd = onNetworkDragEnd,
            onNetworkLongPressStarted = onNetworkLongPressStarted,
            onSelectTokenRequest = onSelectTokenRequest,
            onAssetDragCancel = onAssetDragCancel,
            onAssetDrag = onAssetDrag,
            onAssetDragStart = onAssetDragStart,
            onAssetDragEnd = onAssetDragEnd,
            onAssetLongPressStarted = onAssetLongPressStarted,
        )

        FoldableDestinationAddressWidget(
            state = state,
            onExpandSection = onExpandSection,
            addressFieldState = addressFieldState,
            addressFocusRequester = addressFocusRequester,
            onDstAddressLostFocus = onDstAddressLostFocus,
            onSetOutputAddress = onSetOutputAddress,
            onScanDstAddressRequest = onScanDstAddressRequest,
            onAddressBookClick = onAddressBookClick,
        )

        FoldableAmountWidget(
            state = state,
            addressFieldState = addressFieldState,
            onExpandSection = onExpandSection,
            onGasSettingsClick = onGasSettingsClick,
            focusManager = focusManager,
            onSend = onSend,
            amountInputs = amountInputs,
            optionalInputs = optionalInputs,
        )

        UiSpacer(24.dp)

        AnimatedContent(targetState = state.reapingError, label = "error message") { errorMessage ->
            if (errorMessage != null) {
                Column {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = errorMessage.asString(),
                        color = Theme.v2.colors.backgrounds.amber,
                        style = Theme.menlo.body1,
                    )
                }
            }
        }
    } else if (state.defiType == DeFiNavActions.BOND || state.defiType == DeFiNavActions.UNBOND) {
        FoldableBondDestinationAddress(
            state = state,
            onExpandSection = onExpandSection,
            addressFieldState = addressFieldState,
            addressFocusRequester = addressFocusRequester,
            providerFieldState = providerFieldState,
            onDstAddressLostFocus = onDstAddressLostFocus,
            onSetOutputAddress = onSetOutputAddress,
            onScanDstAddressRequest = onScanDstAddressRequest,
            onAddressBookClick = onAddressBookClick,
            onSetOutputProvider = onSetProvider,
            onScanProviderRequest = onScanProvider,
            onAddressProviderBookClick = onProviderBookClick,
        )

        FoldableAmountWidget(
            state = state,
            addressFieldState = addressFieldState,
            onExpandSection = onExpandSection,
            onGasSettingsClick = onGasSettingsClick,
            focusManager = focusManager,
            onSend = onSend,
            amountInputs = amountInputs,
            optionalInputs = optionalInputs,
        )

        UiSpacer(24.dp)

        AnimatedContent(targetState = state.reapingError, label = "error message") { errorMessage ->
            if (errorMessage != null) {
                Column {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = errorMessage.asString(),
                        color = Theme.v2.colors.backgrounds.amber,
                        style = Theme.menlo.body1,
                    )
                }
            }
        }
    } else if (
        state.defiType == DeFiNavActions.STAKE_RUJI ||
            state.defiType == DeFiNavActions.UNSTAKE_RUJI ||
            state.defiType == DeFiNavActions.STAKE_TCY ||
            state.defiType == DeFiNavActions.UNSTAKE_STCY ||
            state.defiType == DeFiNavActions.STAKE_STCY ||
            state.defiType == DeFiNavActions.UNSTAKE_TCY ||
            state.defiType == DeFiNavActions.MINT_YRUNE ||
            state.defiType == DeFiNavActions.MINT_YTCY ||
            state.defiType == DeFiNavActions.REDEEM_YRUNE ||
            state.defiType == DeFiNavActions.REDEEM_YTCY ||
            state.defiType == DeFiNavActions.WITHDRAW_RUJI ||
            state.defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE ||
            state.defiType == DeFiNavActions.DEPOSIT_USDC_CIRCLE ||
            state.defiType == DeFiNavActions.FREEZE_TRX ||
            state.defiType == DeFiNavActions.UNFREEZE_TRX
    ) {
        FoldableAmountWidget(
            state = state,
            addressFieldState = addressFieldState,
            onExpandSection = onExpandSection,
            onGasSettingsClick = onGasSettingsClick,
            focusManager = focusManager,
            onSend = onSend,
            amountInputs = amountInputs,
            optionalInputs = optionalInputs,
        )
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendFormScreen(
        state =
            SendFormUiModel(
                totalGas = UiText.DynamicString("12.5 Eth"),
                showGasFee = true,
                estimatedFee = UiText.DynamicString("$3.4"),
                expandedSection = SendSections.Amount,
            ),
        addressFieldState = TextFieldState(),
        tokenAmountFieldState = TextFieldState("50"),
        fiatAmountFieldState = TextFieldState("$2,000.56"),
        memoFieldState = TextFieldState(),
        onNetworkDragStart = {},
        onNetworkDrag = {},
        onNetworkDragEnd = {},
        onNetworkDragCancel = {},
        onNetworkLongPressStarted = {},
        onAssetDragStart = {},
        onAssetDrag = {},
        onAssetDragEnd = {},
        onAssetDragCancel = {},
        onAssetLongPressStarted = {},
        operatorFeeFieldState = TextFieldState(),
        providerFieldState = TextFieldState(),
        slippageFieldState = TextFieldState(),
    )
}
