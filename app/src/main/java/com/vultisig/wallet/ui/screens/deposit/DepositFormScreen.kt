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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.SelectionCard
import com.vultisig.wallet.ui.components.containers.VsContainerType
import com.vultisig.wallet.ui.components.containers.VsContainer
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.models.deposit.TokenWithdrawSecureAsset
import com.vultisig.wallet.ui.screens.function.MergeFunctionScreen
import com.vultisig.wallet.ui.screens.function.SwitchFunctionScreen
import com.vultisig.wallet.ui.screens.function.TransferIbcFunctionScreen
import com.vultisig.wallet.ui.screens.function.UnMergeFunctionScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun DepositFormScreen(
    model: DepositFormViewModel = hiltViewModel(),
    vaultId: String,
    chainId: String,
    depositType: String? = null,
    bondAddress: String? = null,
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) {
        model.loadData(vaultId, chainId, depositType, bondAddress)
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
        rewardsAmountFieldState = model.rewardsAmountFieldState,
        slippageFieldState = model.slippageFieldState,
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
        onSlippageLostFocus = model::validateSlippage,
        onDismissError = model::dismissError,
        onSetNodeAddress = model::setNodeAddress,
        onSetProvider = model::setProvider,
        onScan = model::scan,
        onDeposit = model::deposit,

        onSelectChain = model::selectDstChain,
        dstAddress = model.nodeAddressFieldState,
        onDstAddressLostFocus = { },
        onSetDstAddress = model::setNodeAddress,
        amountFieldState = model.tokenAmountFieldState,
        onAmountLostFocus = model::validateTokenAmount,
        memoFieldState = model.customMemoFieldState,
        onMemoLostFocus = { },
        onSelectCoin = model::selectMergeToken,
        onSelectUnMergeCoin = model::selectUnMergeToken,

        thorAddress = model.thorAddressFieldState,
        onThorAddressLostFocus = { },
        onSetThorAddress = { },

        onOpenSelectToken = model::selectToken,

        onLoadRujiBalances = model::onLoadRujiMergeBalances,
        onSelectSecureAsset = model::onSelectSecureAsset
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
    rewardsAmountFieldState: TextFieldState,
    slippageFieldState: TextFieldState,
    onTokenAmountLostFocus: () -> Unit = {},
    onAssetsLostFocus: () -> Unit = {},
    onLpUnitsLostFocus: () -> Unit = {},
    onNodeAddressLostFocus: () -> Unit = {},
    onProviderLostFocus: () -> Unit = {},
    onOperatorFeeLostFocus: () -> Unit = {},
    onCustomMemoLostFocus: () -> Unit = {},
    onSlippageLostFocus: () -> Unit = {},
    basisPointsFieldState: TextFieldState,
    onBasisPointsLostFocus: () -> Unit = {},
    onSelectDepositOption: (DepositOption) -> Unit = {},
    onDismissError: () -> Unit = {},
    onSetNodeAddress: (String) -> Unit = {},
    onSetProvider: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onDeposit: () -> Unit = {},

    onSelectChain: (Chain) -> Unit = {},
    dstAddress: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},

    onSetDstAddress: (String) -> Unit = {},
    amountFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit = {},

    memoFieldState: TextFieldState,
    onMemoLostFocus: () -> Unit = {},

    thorAddress: TextFieldState,
    onThorAddressLostFocus: () -> Unit = {},
    onSetThorAddress: (String) -> Unit = {},

    onSelectCoin: (TokenMergeInfo) -> Unit = {},
    onSelectUnMergeCoin: (TokenMergeInfo) -> Unit = {},

    onOpenSelectToken: () -> Unit = {},

    onLoadRujiBalances: () -> Unit = {},
    onSelectSecureAsset: (TokenWithdrawSecureAsset) -> Unit = {},

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
                    color = Theme.colors.neutrals.n100,
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

            FormSelection(
                selected = state.depositOption,
                options = state.depositOptions,
                onSelectOption = onSelectDepositOption,
                mapTypeToString = { option ->
                    when (option) {
                        DepositOption.Bond -> stringResource(R.string.deposit_option_bond)
                        DepositOption.Unbond -> stringResource(R.string.deposit_option_unbond)
                        DepositOption.Leave -> stringResource(R.string.deposit_option_leave)
                        DepositOption.Stake -> stringResource(R.string.deposit_option_stake)
                        DepositOption.Unstake -> stringResource(R.string.deposit_option_unstake)
                        DepositOption.Custom -> stringResource(R.string.deposit_option_custom)
                        DepositOption.TransferIbc -> stringResource(R.string.deposit_option_ibc_transfer)
                        DepositOption.Switch -> stringResource(R.string.deposit_option_switch)
                        DepositOption.Merge -> stringResource(R.string.deposit_option_merge)
                        DepositOption.UnMerge -> stringResource(R.string.deposit_option_withdraw_ruji)



                        DepositOption.AddCacaoPool -> stringResource(R.string.deposit_option_add_cacao_pool)
                        DepositOption.RemoveCacaoPool -> stringResource(R.string.deposit_option_remove_cacao_pool)
                        DepositOption.SecuredAsset -> stringResource(R.string.deposit_option_secured_assets)
                        DepositOption.WithdrawSecuredAsset -> stringResource(R.string.deposit_option_withdraw_secured)
                    }
                })

            when (val depositOption = state.depositOption) {
                DepositOption.TransferIbc -> {
                    TransferIbcFunctionScreen(
                        selectedChain = state.selectedDstChain,
                        chainList = state.dstChainList,
                        onSelectChain = onSelectChain,

                        selectedToken = state.selectedCoin,
                        coinList = state.coinList,
                        onSelectCoin = onSelectCoin,

                        dstAddress = dstAddress,
                        onDstAddressLostFocus = onDstAddressLostFocus,
                        dstAddressError = state.dstAddressError,
                        onSetDstAddress = onSetDstAddress,

                        balance = state.balance,
                        amountFieldState = amountFieldState,
                        onAmountLostFocus = onAmountLostFocus,
                        amountError = state.amountError,

                        memoFieldState = memoFieldState,
                        onMemoLostFocus = onMemoLostFocus,
                        memoError = state.memoError,
                    )
                }

                DepositOption.Switch -> {
                    SwitchFunctionScreen(
                        selectedToken = state.selectedCoin,
                        coinList = state.coinList,
                        onSelectCoin = onSelectCoin,

                        dstAddress = dstAddress,
                        onDstAddressLostFocus = onDstAddressLostFocus,
                        dstAddressError = state.dstAddressError,
                        onSetDstAddress = onSetDstAddress,

                        thorAddress = thorAddress,
                        onThorAddressLostFocus = onThorAddressLostFocus,
                        thorAddressError = state.thorAddressError,
                        onSetThorAddress = onSetThorAddress,

                        balance = state.balance,
                        amountFieldState = amountFieldState,
                        onAmountLostFocus = onAmountLostFocus,
                        amountError = state.amountError,
                    )
                }

                DepositOption.Merge -> {
                    MergeFunctionScreen(
                        selectedToken = state.selectedCoin,
                        coinList = state.coinList,
                        onSelectCoin = onSelectCoin,

                        balance = state.balance,
                        amountFieldState = amountFieldState,
                        onAmountLostFocus = onAmountLostFocus,
                        amountError = state.amountError,
                    )
                }

                DepositOption.UnMerge -> {
                    UnMergeFunctionScreen(
                        selectedToken = state.selectedUnMergeCoin,
                        coinList = state.coinList,
                        onSelectCoin = onSelectUnMergeCoin,
                        shares = state.sharesBalance,
                        amountFieldState = amountFieldState,
                        onAmountLostFocus = onAmountLostFocus,
                        amountError = state.amountError,
                        onLoadRujiBalances = onLoadRujiBalances,
                    )
                }

                else -> {
                    if (depositChain == Chain.ThorChain && depositOption !in arrayOf(
                            DepositOption.Bond,
                            DepositOption.Unbond,
                            DepositOption.Leave,


                            DepositOption.SecuredAsset,
                            DepositOption.WithdrawSecuredAsset,
                        )
                        && depositOption !in arrayOf(
                            DepositOption.AddCacaoPool,
                            DepositOption.RemoveCacaoPool
                        )
                    ) {
                        FormCard {
                            SelectionCard(
                                title = state.selectedToken.ticker,
                                actionIcon = R.drawable.ic_caret_down,
                                onClick = onOpenSelectToken,
                            )
                        }
                    }

                    val isAddingCacaoPool = depositOption == DepositOption.AddCacaoPool
                    val isRemovingCacaoPool = depositOption == DepositOption.RemoveCacaoPool
                    val unstakableBalance =
                        state.unstakableAmount?.takeIf { it.isNotBlank() } ?: "0"

                    val amountLabel = when {
                        isRemovingCacaoPool -> stringResource(
                            R.string.deposit_form_amount_title,
                            unstakableBalance
                        )



                        else -> stringResource(
                            R.string.deposit_form_amount_title,
                            state.balance.asString()
                        )
                    }

                    val amountHint = when {
                        isRemovingCacaoPool -> stringResource(R.string.deposit_form_unstake_percentage_hint)
                        else -> stringResource(R.string.send_amount_currency_hint)
                    }

                    if (
                        isAddingCacaoPool || isRemovingCacaoPool ||
                        (depositOption != DepositOption.Leave && (depositOption !in listOf(
                            DepositOption.SecuredAsset,
                            DepositOption.WithdrawSecuredAsset
                        )) && depositChain == Chain.ThorChain) ||
                        (depositOption == DepositOption.Custom && depositChain == Chain.MayaChain) ||
                        depositOption == DepositOption.Unstake || depositOption == DepositOption.Stake ||
                        depositOption == DepositOption.AddCacaoPool
                    ) {
                        FormTextFieldCard(
                            title = amountLabel,
                            hint = amountHint,
                            keyboardType = KeyboardType.Number,
                            textFieldState = tokenAmountFieldState,
                            onLostFocus = onTokenAmountLostFocus,
                            error = state.tokenAmountError,
                        )


                    }

                    if (depositOption !in arrayOf(
                            DepositOption.Custom,
                            DepositOption.RemoveCacaoPool, DepositOption.AddCacaoPool,
                            DepositOption.SecuredAsset, DepositOption.WithdrawSecuredAsset
                        )
                    ) {
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
                            PasteIcon(onPaste = onSetProvider)
                            UiSpacer(size = 8.dp)
                        }

                        if (depositChain == Chain.MayaChain) {
                            FormTextFieldCard(
                                title = stringResource(R.string.deposit_form_screen_assets),
                                hint = stringResource(R.string.deposit_form_enter_asset_hint),
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

                    if (depositOption == DepositOption.Bond && depositChain == Chain.ThorChain) {
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






                    if (depositOption == DepositOption.SecuredAsset) {


                        FormTextFieldCard(
                            title = amountLabel,
                            hint = amountHint,
                            keyboardType = KeyboardType.Number,
                            textFieldState = tokenAmountFieldState,
                            onLostFocus = onTokenAmountLostFocus,
                            error = state.tokenAmountError,
                        )


                        UiSpacer(
                            size = 4.dp
                        )

                        Text(
                            text = stringResource(R.string.mint_secured_asset_secure),
                            style = Theme.brockmann.body.s.regular,
                            color = Theme.colors.text.extraLight,
                        )

                        VsContainer(
                            type = VsContainerType.TERTIARY
                        ) {

                            Text(
                                text = stringResource(
                                    R.string.target_asset,
                                    state.selectedToken.getChainName(),
                                    state.selectedToken.ticker
                                ),
                                style = Theme.brockmann.body.s.regular,
                                color = Theme.colors.text.primary,
                                modifier = Modifier
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.address_auto_filled,
                                Chain.ThorChain.raw
                            ),
                            style = Theme.brockmann.body.s.regular,
                            color = Theme.colors.text.extraLight,
                        )

                        VsContainer(
                            type = VsContainerType.TERTIARY
                        ) {
                            Text(
                                text = thorAddress.text.toString(),
                                style = Theme.brockmann.body.s.regular,
                                color = Theme.colors.text.primary,
                                modifier = Modifier
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                            )
                        }

                        Text(
                            text = stringResource(R.string.generated_memo),
                            style = Theme.brockmann.body.s.regular,
                            color = Theme.colors.text.extraLight,
                        )

                        VsContainer(
                            type = VsContainerType.TERTIARY,
                        ) {

                            Text(
                                text = stringResource(
                                    R.string.secure,
                                    thorAddress.text
                                ),
                                style = Theme.brockmann.body.s.regular,
                                color = Theme.colors.text.primary,
                                modifier = Modifier
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                            )
                        }

                    }
                    if (depositOption == DepositOption.WithdrawSecuredAsset) {
                        if (state.availableSecuredAssets.isNotEmpty() &&
                            state.selectedSecuredAsset != TokenWithdrawSecureAsset.EMPTY
                        ) {
                            FormSelection(
                                selected = state.selectedSecuredAsset,
                                options = state.availableSecuredAssets,
                                onSelectOption = onSelectSecureAsset,
                                mapTypeToString = { option ->
                                    option.ticker
                                }
                            )
                            FormTextFieldCard(
                                title = stringResource(
                                    R.string.amount_to_withdraw,
                                    state.balance.asString()
                                ),
                                hint = amountHint,
                                keyboardType = KeyboardType.Number,
                                textFieldState = tokenAmountFieldState,
                                onLostFocus = onTokenAmountLostFocus,
                                error = state.tokenAmountError,
                            )
                        }
                    }
                }
            }
            UiSpacer(size = 80.dp)
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            onClick = {
                focusManager.clearFocus()
                onDeposit()
            },
            state = if (state.isLoading)
                VsButtonState.Disabled
            else VsButtonState.Enabled,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(all = 16.dp),
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
        dstAddress = TextFieldState(),
        amountFieldState = TextFieldState(),
        memoFieldState = TextFieldState(),
        thorAddress = TextFieldState(),
        rewardsAmountFieldState = TextFieldState(),
        slippageFieldState = TextFieldState(),
    )
}