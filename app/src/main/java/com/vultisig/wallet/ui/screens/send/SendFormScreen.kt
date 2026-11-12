@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.send.AmountFraction
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.models.send.exceedsStakeBalance
import com.vultisig.wallet.ui.models.send.isContinueDisabled
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.tron.TronResourceTypeTab
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

internal fun NavGraphBuilder.sendScreen() {
    composable<Route.Send.SendMain> {
        val viewModel: SendFormViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsState()

        val addressFocusRequester = remember { FocusRequester() }
        val amountFocusRequester = remember { FocusRequester() }

        // DeFi forms only; a plain send focuses through pendingFocusField in the step on screen.
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
            destinationTagFieldState = viewModel.destinationTagFieldState,
            onDstAddressLostFocus = { /* no-op */ },
            onTokenAmountLostFocus = viewModel::validateTokenAmount,
            onDismissError = viewModel::dismissError,
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
            onSelectStep = viewModel::selectStep,
            onPendingFocusApplied = viewModel::onPendingFocusApplied,
            assetSearchFieldState = viewModel.assetSearchFieldState,
            onSelectAssetChainFilter = viewModel::selectAssetChainFilter,
            onSelectAsset = viewModel::selectAsset,
            onAddCustomToken = viewModel::addCustomToken,
            slippageFieldState = viewModel.slippageFieldState,
            onAutoCompound = { viewModel.onAutoCompound(it) },
            onTronResourceTypeChange = viewModel::setTronResourceType,
        )

        val selectedChain = state.selectedCoin?.model?.address?.chain
        val specific = state.specific

        if (state.showGasSettings && selectedChain != null && specific != null) {
            GasSettingsScreen(
                chain = selectedChain,
                specific = specific,
                savedSettings = viewModel.savedGasSettings(selectedChain),
                onSaveGasSettings = viewModel::saveGasSettings,
                onDismissGasSettings = viewModel::dismissGasSettings,
            )
        }
    }
}

@Composable
internal fun SendFormScreen(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    addressFocusRequester: FocusRequester,
    amountFocusRequester: FocusRequester,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    memoFieldState: TextFieldState,
    destinationTagFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onChooseMaxTokenAmount: () -> Unit = {},
    onChoosePercentageAmount: (AmountFraction) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScanDstAddressRequest: () -> Unit = {},
    onSend: () -> Unit = {},
    onRefreshRequest: () -> Unit = {},
    onGasSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToggleAmountInputType: (Boolean) -> Unit = {},
    onExpandSection: (SendSections) -> Unit = {},
    onSelectStep: (SendSections) -> Unit = {},
    onPendingFocusApplied: () -> Unit = {},

    // plain-send asset step
    assetSearchFieldState: TextFieldState,
    onSelectAssetChainFilter: (Chain?) -> Unit = {},
    onSelectAsset: (Coin) -> Unit = {},
    onAddCustomToken: () -> Unit = {},

    // trade
    slippageFieldState: TextFieldState,

    // autocompound
    onAutoCompound: (Boolean) -> Unit = {},

    // tron freeze/unfreeze
    onTronResourceTypeChange: (TronResourceType) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
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
            destinationTagFieldState = destinationTagFieldState,
            slippageFieldState = slippageFieldState,
            onAutoCompoundCheckedChange = onAutoCompound,
        )

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    if (state.defiType == null) {
        SendStepFlowScreen(
            state = state,
            onBackClick = onBackClick,
            onSelectStep = onSelectStep,
            onContinue = onSend,
            onRefreshRequest = onRefreshRequest,
        ) { step, contentPadding ->
            // Each pane renders the step it belongs to, also while it slides out.
            val paneState = state.copy(expandedSection = step)
            // A sliding-out pane's fields take no focus, so typing never lands behind the new step.
            val isActive = step == state.expandedSection
            when (step) {
                SendSections.Asset ->
                    SendAssetStep(
                        state = paneState,
                        searchFieldState = assetSearchFieldState,
                        contentPadding = contentPadding,
                        onSelectChainFilter = onSelectAssetChainFilter,
                        onSelectAsset = onSelectAsset,
                        onAddCustomToken = onAddCustomToken,
                    )
                SendSections.Address ->
                    SendStepScrollPane(contentPadding) {
                        SendAddressStep(
                            state = paneState,
                            addressFieldState = addressFieldState,
                            addressFocusRequester = addressFocusRequester,
                            onDstAddressLostFocus = onDstAddressLostFocus,
                            onSetOutputAddress = onSetOutputAddress,
                            onScanDstAddressRequest = onScanDstAddressRequest,
                            onAddressBookClick = onAddressBookClick,
                            onPendingFocusApplied = onPendingFocusApplied,
                            isActive = isActive,
                        )
                    }
                SendSections.Amount ->
                    SendStepScrollPane(contentPadding) {
                        SendAmountStep(
                            state = paneState,
                            amountInputs = amountInputs,
                            optionalInputs = optionalInputs,
                            focusManager = focusManager,
                            onGasSettingsClick = onGasSettingsClick,
                            onSend = onSend,
                            onPendingFocusApplied = onPendingFocusApplied,
                            isActive = isActive,
                        )
                    }
            }
        }
        return
    }

    V2Scaffold(
        title =
            when (state.defiType) {
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.STAKE_STCY,
                DeFiNavActions.STAKE_YBRUNE -> stringResource(R.string.stake_screen_title)
                DeFiNavActions.UNSTAKE_TCY,
                DeFiNavActions.UNSTAKE_RUJI,
                DeFiNavActions.UNSTAKE_STCY,
                DeFiNavActions.UNSTAKE_YBRUNE -> stringResource(R.string.unstake_screen_title)
                DeFiNavActions.MINT_YRUNE,
                DeFiNavActions.MINT_YTCY -> stringResource(R.string.mint_screen_title)
                DeFiNavActions.REDEEM_YRUNE,
                DeFiNavActions.REDEEM_YTCY -> stringResource(R.string.redeem_screen_title)
                DeFiNavActions.WITHDRAW_RUJI -> stringResource(R.string.rewards_screen_title)
                DeFiNavActions.WITHDRAW_USDC_CIRCLE -> stringResource(R.string.withdraw)
                DeFiNavActions.FREEZE_TRX -> stringResource(R.string.tron_freeze_screen_title)
                DeFiNavActions.UNFREEZE_TRX -> stringResource(R.string.tron_unfreeze_screen_title)
                else -> stringResource(R.string.send_screen_title)
            },
        onBackClick = onBackClick,
        bottomBar = {
            val isContinueDisabled =
                state.isContinueDisabled() ||
                    state.exceedsStakeBalance(tokenAmountFieldState.text.toString())
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (isContinueDisabled) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = {
                    if (!isContinueDisabled) {
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
                val isCircleMode = state.defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        if (isCircleMode) Modifier.fillMaxHeight()
                        else Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SendFormContent(
                        state = state,
                        onExpandSection = onExpandSection,
                        addressFieldState = addressFieldState,
                        onGasSettingsClick = onGasSettingsClick,
                        focusManager = focusManager,
                        onSend = onSend,
                        amountInputs = amountInputs,
                        optionalInputs = optionalInputs,
                        onTronResourceTypeChange = onTronResourceTypeChange,
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
    addressFieldState: TextFieldState,
    onGasSettingsClick: () -> Unit,
    focusManager: FocusManager,
    onSend: () -> Unit,
    amountInputs: AmountInputs,
    optionalInputs: OptionalInputs,
    onTronResourceTypeChange: (TronResourceType) -> Unit,
) {
    val resourceType = state.tronResourceType
    if (resourceType != null) {
        TronResourceTypeTab(
            selected = resourceType,
            onSelectionChange = onTronResourceTypeChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    if (state.hasTronFrozenBalancesError) {
        Text(
            text = stringResource(R.string.tron_frozen_balances_error),
            color = Theme.v2.colors.alerts.error,
            style = Theme.brockmann.supplementary.footnote,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

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

    ReapingErrorMessage(error = state.feeShortfallError ?: state.reapingError)
}

/**
 * Displays the amount-validation (reaping) error returned by the view model.
 *
 * @param error the error to show, or null when the amount is valid.
 */
@Composable
internal fun ReapingErrorMessage(error: UiText?) {
    AnimatedContent(targetState = error, label = "error message") { errorMessage ->
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
        addressFocusRequester = remember { FocusRequester() },
        amountFocusRequester = remember { FocusRequester() },
        tokenAmountFieldState = TextFieldState("50"),
        fiatAmountFieldState = TextFieldState("$2,000.56"),
        memoFieldState = TextFieldState(),
        destinationTagFieldState = TextFieldState(),
        assetSearchFieldState = TextFieldState(),
        slippageFieldState = TextFieldState(),
    )
}
