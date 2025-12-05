@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.screens.send

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.components.v2.fastselection.contentWithFastSelection
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.send.AddressBookType
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.deposit.components.AutoCompoundToggle
import com.vultisig.wallet.ui.screens.swap.TokenChip
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString

internal fun NavGraphBuilder.sendScreen(
    navController: NavHostController,
) {
    contentWithFastSelection<Route.Send.SendMain, Route.Send>(
        navController = navController
    ) { onNetworkDragStart, onNetworkDrag, onNetworkDragEnd ->
        val viewModel: SendFormViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsState()

        SendFormScreen(
            state = state,
            addressFieldState = viewModel.addressFieldState,
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
            onToogleAmountInputType = viewModel::toggleAmountInputType,
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
            onAutoCompound = { viewModel.onAutoCompound(it) }
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
    onChoosePercentageAmount: (Float) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScanDstAddressRequest: () -> Unit = {},
    onSend: () -> Unit = {},
    onAddressProviderBookClick: () -> Unit = {},
    onScanProviderAddressRequest: () -> Unit = {},
    onSetProviderAddressRequest: (String) -> Unit = {},

    onRefreshRequest: () -> Unit = {},
    onGasSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToogleAmountInputType: (Boolean) -> Unit = {},
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
        title = when (state.defiType) {
            DeFiNavActions.STAKE_RUJI, DeFiNavActions.STAKE_TCY -> stringResource(R.string.stake_screen_title)
            DeFiNavActions.UNSTAKE_TCY, DeFiNavActions.UNSTAKE_RUJI -> stringResource(R.string.unstake_screen_title)
            DeFiNavActions.MINT_YRUNE, DeFiNavActions.MINT_YTCY -> stringResource(R.string.mint_screen_title)
            DeFiNavActions.REDEEM_YRUNE, DeFiNavActions.REDEEM_YTCY -> stringResource(R.string.redeem_screen_title)
            DeFiNavActions.BOND -> stringResource(R.string.bond_screen_title)
            DeFiNavActions.UNBOND -> stringResource(R.string.unbond_screen_title)
            else -> stringResource(R.string.send_screen_title)
        },
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (state.isLoading)
                    VsButtonState.Disabled
                else
                    VsButtonState.Enabled,
                onClick = {
                    if (!state.isLoading) {
                        focusManager.clearFocus()
                        onSend()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
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
                        state = pullToRefreshState
                    )
                },
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
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
                        onDstAddressLostFocus = onDstAddressLostFocus,
                        onSetOutputAddress = onSetOutputAddress,
                        onScanDstAddressRequest = onScanDstAddressRequest,
                        onAddressBookClick = onAddressBookClick,
                        onGasSettingsClick = onGasSettingsClick,
                        tokenAmountFieldState = tokenAmountFieldState,
                        fiatAmountFieldState = fiatAmountFieldState,
                        focusManager = focusManager,
                        onSend = onSend,
                        onToogleAmountInputType = onToogleAmountInputType,
                        onChoosePercentageAmount = onChoosePercentageAmount,
                        onChooseMaxTokenAmount = onChooseMaxTokenAmount,
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
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
    onGasSettingsClick: () -> Unit,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    focusManager: FocusManager,
    onSend: () -> Unit,
    onToogleAmountInputType: (Boolean) -> Unit,
    onChoosePercentageAmount: (Float) -> Unit,
    onChooseMaxTokenAmount: () -> Unit,
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
    onAutoCompoundCheckedChange: (Boolean) -> Unit
) {
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
            onAssetLongPressStarted = onAssetLongPressStarted
        )

        FoldableDestinationAddressWidget(
            state = state,
            onExpandSection = onExpandSection,
            addressFieldState = addressFieldState,
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
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            focusManager = focusManager,
            onSend = onSend,
            onToogleAmountInputType = onToogleAmountInputType,
            onChoosePercentageAmount = onChoosePercentageAmount,
            onChooseMaxTokenAmount = onChooseMaxTokenAmount,
            memoFieldState = memoFieldState,
            operatorFeeFieldState = operatorFeeFieldState,
            slippageTexFieldState = slippageFieldState,
            onAutoCompoundCheckedChange = onAutoCompoundCheckedChange,
        )

        UiSpacer(24.dp)

        AnimatedContent(
            targetState = state.reapingError,
            label = "error message"
        ) { errorMessage ->
            if (errorMessage != null) {
                Column {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = errorMessage.asString(),
                        color = Theme.v2.colors.backgrounds.amber,
                        style = Theme.menlo.body1
                    )
                }
            }
        }
    } else if (state.defiType == DeFiNavActions.BOND || state.defiType == DeFiNavActions.UNBOND) {
        FoldableBondDestinationAddress(
            state = state,
            onExpandSection = onExpandSection,
            addressFieldState = addressFieldState,
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
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            focusManager = focusManager,
            onSend = onSend,
            onToogleAmountInputType = onToogleAmountInputType,
            onChoosePercentageAmount = onChoosePercentageAmount,
            onChooseMaxTokenAmount = onChooseMaxTokenAmount,
            memoFieldState = memoFieldState,
            operatorFeeFieldState = operatorFeeFieldState,
            slippageTexFieldState = slippageFieldState,
            onAutoCompoundCheckedChange = onAutoCompoundCheckedChange,
        )

        UiSpacer(24.dp)

        AnimatedContent(
            targetState = state.reapingError,
            label = "error message"
        ) { errorMessage ->
            if (errorMessage != null) {
                Column {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = errorMessage.asString(),
                        color = Theme.v2.colors.backgrounds.amber,
                        style = Theme.menlo.body1
                    )
                }
            }
        }
    } else if (state.defiType == DeFiNavActions.STAKE_RUJI
        || state.defiType == DeFiNavActions.UNSTAKE_RUJI
        || state.defiType == DeFiNavActions.STAKE_TCY
        || state.defiType == DeFiNavActions.UNSTAKE_TCY
        || state.defiType == DeFiNavActions.MINT_YRUNE
        || state.defiType == DeFiNavActions.MINT_YTCY
        || state.defiType == DeFiNavActions.REDEEM_YRUNE
        || state.defiType == DeFiNavActions.REDEEM_YTCY
        || state.defiType == DeFiNavActions.WITHDRAW_RUJI
    ) {
        FoldableAmountWidget(
            state = state,
            addressFieldState = addressFieldState,
            onExpandSection = onExpandSection,
            onGasSettingsClick = onGasSettingsClick,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            focusManager = focusManager,
            onSend = onSend,
            onToogleAmountInputType = onToogleAmountInputType,
            onChoosePercentageAmount = onChoosePercentageAmount,
            onChooseMaxTokenAmount = onChooseMaxTokenAmount,
            memoFieldState = memoFieldState,
            operatorFeeFieldState = operatorFeeFieldState,
            slippageTexFieldState = slippageFieldState,
            onAutoCompoundCheckedChange = onAutoCompoundCheckedChange,
        )
    }
}

@Composable
private fun FoldableAmountWidget(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    onExpandSection: (SendSections) -> Unit,
    onGasSettingsClick: () -> Unit,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    focusManager: FocusManager,
    onSend: () -> Unit,
    onToogleAmountInputType: (Boolean) -> Unit,
    onChoosePercentageAmount: (Float) -> Unit,
    onChooseMaxTokenAmount: () -> Unit,
    onAutoCompoundCheckedChange: (Boolean) -> Unit,
    memoFieldState: TextFieldState,
    operatorFeeFieldState: TextFieldState,
    slippageTexFieldState: TextFieldState,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Amount,
        onToggle = {
            if (state.isDstAddressComplete &&
                addressFieldState.text.isNotEmpty()
            ) {
                onExpandSection(SendSections.Amount)
            }
        },
        expandedTitleActions = {
            if (state.hasGasSettings) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .weight(1f),
                ) {
                    UiIcon(
                        drawableResId = R.drawable.advance_gas_settings,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                        onClick = onGasSettingsClick,
                    )
                }
            }
        },
        title = stringResource(R.string.send_amount)
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                )
        ) {
            Box(
                modifier = Modifier
                    .height(211.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = 54.dp,
                        )
                        .align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val primaryAmountText: String
                    val secondaryAmountText: String
                    val primaryFieldState: TextFieldState
                    val secondaryFieldState: TextFieldState

                    if (state.usingTokenAmountInput) {
                        primaryAmountText = state.selectedCoin?.title ?: ""
                        secondaryAmountText = state.fiatCurrency
                        primaryFieldState = tokenAmountFieldState
                        secondaryFieldState = fiatAmountFieldState
                    } else {
                        primaryAmountText = state.fiatCurrency
                        secondaryAmountText = state.selectedCoin?.title ?: ""
                        primaryFieldState = fiatAmountFieldState
                        secondaryFieldState = tokenAmountFieldState
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        BasicTextField(
                            state = primaryFieldState,
                            lineLimits = TextFieldLineLimits.MultiLine(
                                maxHeightInLines = 3,
                            ),
                            textStyle = Theme.brockmann.headings.largeTitle
                                .copy(
                                    color = Theme.v2.colors.text.primary,
                                    textAlign = TextAlign.Center,
                                ),
                            cursorBrush = Theme.cursorBrush,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Send,
                            ),
                            onKeyboardAction = {
                                focusManager.clearFocus()
                                onSend()
                            },
                            modifier = Modifier
                                .width(IntrinsicSize.Min),
                            decorator = { textField ->
                                if (primaryFieldState.text.isEmpty()) {
                                    Text(
                                        text = "0",
                                        color = Theme.v2.colors.text.light,
                                        style = Theme.brockmann.headings.largeTitle,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .wrapContentWidth()
                                    )
                                }
                                textField()
                            }
                        )

                        Text(
                            text = " $primaryAmountText",
                            color = Theme.v2.colors.text.primary,
                            style = Theme.brockmann.headings.largeTitle,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Text(
                        text = "${secondaryFieldState.text.ifEmpty { "0" }} $secondaryAmountText",
                        color = Theme.v2.colors.text.extraLight,
                        style = Theme.brockmann.body.s.medium,
                        textAlign = TextAlign.Center,
                    )
                }

                TokenFiatToggle(
                    isTokenSelected = state.usingTokenAmountInput,
                    onTokenSelected = {
                        onToogleAmountInputType(it)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                )
            }

            UiSpacer(12.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PercentageChip(
                    title = "25%",
                    isSelected = false,
                    onClick = { onChoosePercentageAmount(0.25f) },
                    modifier = Modifier
                        .weight(1f),
                )

                PercentageChip(
                    title = "50%",
                    isSelected = false,
                    onClick = { onChoosePercentageAmount(0.5f) },
                    modifier = Modifier
                        .weight(1f),
                )

                PercentageChip(
                    title = "75%",
                    isSelected = false,
                    onClick = { onChoosePercentageAmount(0.75f) },
                    modifier = Modifier
                        .weight(1f),
                )

                PercentageChip(
                    title = stringResource(R.string.send_screen_max),
                    isSelected = false,
                    onClick = onChooseMaxTokenAmount,
                    modifier = Modifier
                        .weight(1f),
                )
            }

            UiSpacer(12.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(
                        color = Theme.v2.colors.backgrounds.secondary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(
                        all = 16.dp,
                    )
            ) {
                Text(
                    text = stringResource(R.string.send_form_balance_available),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )

                Text(
                    text = state.selectedCoin?.balance ?: "",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.light,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f),
                )
            }

            UiSpacer(12.dp)

            // memo
            if (state.hasMemo && state.defiType == null) {
                var isMemoExpanded by remember { mutableStateOf(false) }

                val rotationAngle by animateFloatAsState(
                    targetValue = if (isMemoExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "caretRotation"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            isMemoExpanded = !isMemoExpanded
                        }
                        .padding(
                            vertical = 2.dp,
                        )
                ) {
                    Text(
                        text = stringResource(R.string.send_form_add_memo),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.extraLight,
                        modifier = Modifier
                            .weight(1f),
                    )

                    UiIcon(
                        drawableResId = R.drawable.ic_caret_down,
                        tint = Theme.v2.colors.text.primary,
                        size = 16.dp,
                        modifier = Modifier
                            .rotate(rotationAngle)
                    )
                }

                UiSpacer(12.dp)

                AnimatedVisibility(
                    visible = isMemoExpanded,
                ) {
                    val clipboardData = VsClipboardService.getClipboardData()
                    VsTextInputField(
                        textFieldState = memoFieldState,
                        hint = stringResource(R.string.send_form_enter_memo),
                        trailingIcon = R.drawable.paste,
                        onTrailingIconClick = {
                            clipboardData.value
                                ?.takeIf { it.isNotEmpty() }
                                ?.let {
                                    memoFieldState.setTextAndPlaceCursorAtEnd(
                                        text = it
                                    )
                                }
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                    )

                    UiSpacer(12.dp)
                }
            }

            if (state.defiType == DeFiNavActions.BOND) {
                Column(
                    modifier = Modifier.padding(
                        vertical = 2.dp,
                    )
                ) {
                    Text(
                        text = stringResource(R.string.bond_operator_fees_basis_point),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.extraLight,
                    )

                    UiSpacer(12.dp)

                    VsTextInputField(
                        textFieldState = operatorFeeFieldState,
                        hint = "0",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UiSpacer(12.dp)
                }
            }

            if (state.defiType == DeFiNavActions.REDEEM_YRUNE
                || state.defiType == DeFiNavActions.REDEEM_YTCY
            ) {
                Column(
                    modifier = Modifier.padding(
                        vertical = 2.dp,
                    )
                ) {
                    Text(
                        text = stringResource(R.string.deposit_form_operator_slippage_title),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.extraLight,
                    )

                    UiSpacer(12.dp)

                    VsTextInputField(
                        textFieldState = slippageTexFieldState,
                        hint = stringResource(R.string.slippage_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UiSpacer(12.dp)
                }
            }

            if (state.defiType == DeFiNavActions.STAKE_TCY) {
                AutoCompoundToggle(
                    title = stringResource(R.string.tcy_auto_compound_enable_title),
                    subtitle = stringResource(R.string.tcy_auto_compound_enable_subtitle),
                    isChecked = state.isAutocompound,
                    onCheckedChange = onAutoCompoundCheckedChange,
                )
            }

            if (state.defiType == DeFiNavActions.UNSTAKE_TCY) {
                AutoCompoundToggle(
                    title = stringResource(R.string.tcy_auto_compound_unstake_title),
                    subtitle = stringResource(R.string.tcy_auto_compound_unstake_subtitle),
                    isChecked = state.isAutocompound,
                    onCheckedChange = onAutoCompoundCheckedChange,
                )
            }

            if (state.showGasFee) {
                FadingHorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth(),
                )

                UiSpacer(12.dp)

                EstimatedNetworkFee(
                    tokenGas = state.totalGas.asString(),
                    fiatGas = state.estimatedFee.asString(),
                )
            }
        }
    }
}

@Composable
private fun FoldableDestinationAddressWidget(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    // dst address
    addressFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Address,
        complete = state.isDstAddressComplete,
        title = stringResource(R.string.add_address_address_title),
        onToggle = {
            onExpandSection(SendSections.Address)
        },
        completeTitleContent = {
            Text(
                text = addressFieldState.text.toString(),
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                )
        ) {
            Text(
                text = stringResource(R.string.send_from_address),
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        color = Theme.v2.colors.backgrounds.secondary,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = state.srcVaultName,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.supplementary.caption,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )

                Text(
                    text = state.srcAddress,
                    color = Theme.v2.colors.text.extraLight,
                    style = Theme.brockmann.supplementary.caption,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            UiSpacer(16.dp)

            Text(
                text = when (state.defiType) {
                    DeFiNavActions.BOND, DeFiNavActions.UNBOND -> stringResource(R.string.bond_node_address)
                    else -> stringResource(R.string.send_to_address)
                },
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = addressFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                onFocusChanged = {
                    if (!it) {
                        onDstAddressLostFocus()
                    }
                },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                innerState = if (state.dstAddressError != null)
                    VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default,
                footNote = state.dstAddressError?.asString(),
                modifier = Modifier
                    .fillMaxWidth(),
            )

            UiSpacer(16.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasteIcon(
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onPaste = onSetOutputAddress
                )

                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onScanDstAddressRequest,
                )

                UiIcon(
                    drawableResId = R.drawable.ic_bookmark,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onAddressBookClick,
                )
            }
        }
    }
}

@Composable
private fun FoldableBondDestinationAddress(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    // dst address
    addressFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
    // bond provider
    providerFieldState: TextFieldState,
    onSetOutputProvider: (String) -> Unit,
    onScanProviderRequest: () -> Unit,
    onAddressProviderBookClick: () -> Unit,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Address,
        complete = state.isDstAddressComplete,
        title = stringResource(R.string.add_address_address_title),
        onToggle = {
            onExpandSection(SendSections.Address)
        },
        completeTitleContent = {
            Text(
                text = addressFieldState.text.toString(),
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                )
        ) {
            Text(
                text = when (state.defiType) {
                    null, DeFiNavActions.BOND, DeFiNavActions.UNBOND -> stringResource(R.string.bond_node_address)
                    else -> stringResource(R.string.send_to_address)
                },
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = addressFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                onFocusChanged = {
                    if (!it) {
                        onDstAddressLostFocus()
                    }
                },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                innerState = if (state.dstAddressError != null)
                    VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default,
                footNote = state.dstAddressError?.asString(),
                modifier = Modifier
                    .fillMaxWidth(),
            )

            UiSpacer(16.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasteIcon(
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onPaste = onSetOutputAddress
                )

                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onScanDstAddressRequest,
                )

                UiIcon(
                    drawableResId = R.drawable.ic_bookmark,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onAddressBookClick,
                )
            }

            UiSpacer(12.dp)

            Text(
                text = stringResource(R.string.bond_provider_optional),
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = providerFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                innerState = if (state.bondProviderError != null) {
                    VsTextInputFieldInnerState.Error
                } else {
                    VsTextInputFieldInnerState.Default
                },
                footNote = state.bondProviderError?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(16.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasteIcon(
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onPaste = onSetOutputProvider
                )

                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onScanProviderRequest,
                )

                UiIcon(
                    drawableResId = R.drawable.ic_bookmark,
                    size = 20.dp,
                    modifier = Modifier
                        .vsClickableBackground()
                        .padding(all = 12.dp)
                        .weight(1f),
                    onClick = onAddressProviderBookClick,
                )
            }
        }
    }
}

@Composable
private fun FoldableAssetWidget(
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
    onAssetLongPressStarted: (Offset) -> Unit
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Asset,
        onToggle = {
            onExpandSection(SendSections.Asset)
        },
        complete = true,
        title = stringResource(R.string.form_token_selection_asset),
        completeTitleContent = {
            Row(
                modifier = Modifier.weight(1f)
            ) {
                val selectedToken = state.selectedCoin

                TokenLogo(
                    errorLogoModifier = Modifier
                        .size(16.dp)
                        .background(Theme.v2.colors.neutrals.n100),
                    logo = selectedToken?.tokenLogo ?: "",
                    title = selectedToken?.title ?: "",
                    modifier = Modifier
                        .size(16.dp)
                )

                UiSpacer(4.dp)

                Text(
                    text = selectedToken?.title ?: "",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.extraLight,
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                )
        ) {
            ChainSelector(
                title = stringResource(R.string.send_from_address),
                // TODO selectedChain should not be nullable
                //  or default value should be something else
                chain = state.selectedCoin?.model?.address?.chain
                    ?: Chain.ThorChain,
                onClick = onSelectNetworkRequest,
                onDragCancel = onNetworkDragCancel,
                onDrag = onNetworkDrag,
                onDragStart = onNetworkDragStart,
                onDragEnd = onNetworkDragEnd,
                onLongPressStarted = onNetworkLongPressStarted,
            )

            UiSpacer(12.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                TokenChip(
                    selectedToken = state.selectedCoin,
                    onSelectTokenClick = onSelectTokenRequest,

                    onDragCancel = onAssetDragCancel,
                    onDrag = onAssetDrag,
                    onDragStart = onAssetDragStart,
                    onDragEnd = onAssetDragEnd,
                    onLongPressStarted = onAssetLongPressStarted,

                    )

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f),
                ) {
                    state.selectedCoin?.let { token ->
                        Text(
                            text = stringResource(
                                R.string.form_token_selection_balance,
                                token.balance ?: ""
                            ),
                            color = Theme.v2.colors.text.light,
                            style = Theme.brockmann.body.s.medium,
                            textAlign = TextAlign.End,
                        )

                        UiSpacer(2.dp)

                        token.fiatValue?.let { fiatValue ->
                            Text(
                                text = fiatValue,
                                textAlign = TextAlign.End,
                                color = Theme.v2.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.caption,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EstimatedNetworkFee(
    tokenGas: String,
    fiatGas: String,
    isLoading: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.send_form_est_network_fee),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.extraLight,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f),
        ) {
            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )

                UiSpacer(6.dp)

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )
            } else {
                Text(
                    text = tokenGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )

                Text(
                    text = fiatGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.extraLight,
                )
            }
        }
    }
}

@Composable
internal fun FadingHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Theme.v2.colors.backgrounds.secondary.copy(alpha = 0f),
                        Color(0xFF284570),
                        Theme.v2.colors.backgrounds.secondary.copy(alpha = 0f),
                    ),
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY,
                    tileMode = TileMode.Clamp
                )
            )
    )
}

@Composable
private fun Modifier.vsClickableBackground() =
    border(
        border = BorderStroke(
            width = 1.dp,
            color = Theme.v2.colors.border.light,
        ),
        shape = RoundedCornerShape(12.dp),
    )
        .background(
            color = Theme.v2.colors.backgrounds.secondary,
            shape = RoundedCornerShape(12.dp),
        )

@Composable
private fun PercentageChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.light,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.v2.colors.primary.accent3,
                        shape = RoundedCornerShape(99.dp),
                    )
                else
                    Modifier.border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = RoundedCornerShape(99.dp),
                    )
            )
            .padding(
                all = 4.dp,
            )
    )
}

@Composable
private fun TokenFiatToggle(
    isTokenSelected: Boolean,
    onTokenSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(
                color = Theme.v2.colors.backgrounds.secondary,
                shape = RoundedCornerShape(99.dp)
            )
            .padding(
                all = 4.dp,
            )
    ) {
        ToggleButton(
            drawableResId = R.drawable.ic_coins,
            isSelected = isTokenSelected,
            onClick = { onTokenSelected(true) },
        )

        ToggleButton(
            drawableResId = R.drawable.ic_dollar_sign,
            isSelected = !isTokenSelected,
            onClick = { onTokenSelected(false) },
        )
    }
}

@Composable
private fun ToggleButton(
    @DrawableRes drawableResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    UiIcon(
        drawableResId = drawableResId,
        size = 16.dp,
        tint = Theme.v2.colors.text.light,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.v2.colors.primary.accent3,
                        shape = CircleShape,
                    )
                else Modifier
            )
            .padding(all = 8.dp)
    )
}


@Composable
private fun FoldableSection(
    expanded: Boolean = false,
    complete: Boolean = false,

    completeTitleContent: (@Composable RowScope.() -> Unit)? = null,
    expandedTitleActions: (@Composable RowScope.() -> Unit)? = null,

    onToggle: () -> Unit = {},

    title: String,

    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.normal,
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(all = 16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )

            if (expanded) {
                expandedTitleActions?.invoke(this)
            } else {
                if (complete) {
                    completeTitleContent?.invoke(this)

                    UiIcon(
                        drawableResId = R.drawable.ic_check,
                        size = 16.dp,
                        tint = Theme.v2.colors.alerts.success,
                    )

                    UiSpacer(1.dp)

                    UiIcon(
                        drawableResId = R.drawable.pencil,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
        ) {
            FadingHorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                    ),
            )

            content()
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
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