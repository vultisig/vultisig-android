package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.send.AddressManager
import com.vultisig.wallet.ui.models.send.AmountManager
import com.vultisig.wallet.ui.models.send.ChainValidationService
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import wallet.core.jni.proto.Bitcoin

/**
 * Bundle of the eight submit strategies produced by [SendStrategyFactory.create] for a single
 * `SendFormViewModel` instance.
 */
internal data class SendStrategies(
    val default: DefaultSendStrategy,
    val bond: BondStrategy,
    val unbond: UnbondStrategy,
    val stake: StakeStrategy,
    val unstake: UnstakeStrategy,
    val mint: MintStrategy,
    val redeem: RedeemStrategy,
    val withdrawUsdcCircle: WithdrawUsdcCircleStrategy,
) {

    /**
     * Submits the form using the strategy that matches [defiType].
     *
     * @param defiType the active DeFi action, or `null` for a plain send.
     */
    fun submitFor(defiType: DeFiNavActions?) {
        when (defiType) {
            DeFiNavActions.BOND -> bond.submit()
            DeFiNavActions.UNBOND -> unbond.submit()
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY -> stake.submit()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.WITHDRAW_RUJI -> unstake.submit()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> mint.submit()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> redeem.submit()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> withdrawUsdcCircle.submit()

            null,
            DeFiNavActions.DEPOSIT_USDC_CIRCLE,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.ADD_LP,
            DeFiNavActions.REMOVE_LP,
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> default.submit()
        }
    }
}

/**
 * Per-`SendFormViewModel` state needed to construct the submit strategies — scope, field states,
 * shared flows, helpers, providers, and UI callbacks.
 *
 * Bundles the per-instance inputs so [SendStrategyFactory.create] (and the deferred manager-factory
 * follow-up) take a single parameter instead of a wide positional argument list.
 */
internal data class SendStrategyContext(
    val scope: CoroutineScope,
    val addressFieldState: TextFieldState,
    val tokenAmountFieldState: TextFieldState,
    val fiatAmountFieldState: TextFieldState,
    val memoFieldState: TextFieldState,
    val slippageFieldState: TextFieldState,
    val operatorFeesBondFieldState: TextFieldState,
    val providerBondFieldState: TextFieldState,
    val accountValidator: AccountValidator,
    val bitcoinPlanService: BitcoinPlanService,
    val addressManager: AddressManager,
    val amountManager: AmountManager,
    val gasSettings: StateFlow<GasSettings?>,
    val planBtc: MutableStateFlow<Bitcoin.TransactionPlan?>,
    val planFee: MutableStateFlow<Long?>,
    val accounts: StateFlow<List<Account>>,
    val appCurrency: StateFlow<AppCurrency>,
    val vaultIdProvider: () -> String?,
    val selectedAccountProvider: () -> Account?,
    val defiTypeProvider: () -> DeFiNavActions?,
    val isAutocompoundProvider: () -> Boolean,
    val mscaAddressProvider: () -> String?,
    val currentTronFrozenBalanceProvider: () -> BigDecimal?,
    val expandSection: (SendSections) -> Unit,
    val emitFocusField: (SendFocusField) -> Unit,
    val showLoading: () -> Unit,
    val hideLoading: () -> Unit,
    val showError: (UiText) -> Unit,
)

/**
 * Builds the per-`SendFormViewModel` submit strategies, holding the shared singleton dependencies
 * once so each strategy doesn't have to receive them individually.
 */
internal class SendStrategyFactory
@Inject
constructor(
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val addressParserRepository: AddressParserRepository,
    private val chainValidationService: ChainValidationService,
    private val navigator: Navigator<Destination>,
) {

    /**
     * Wires the per-instance ViewModel state in [context] with the shared dependencies and returns
     * the eight strategies.
     */
    fun create(context: SendStrategyContext): SendStrategies =
        SendStrategies(
            default =
                DefaultSendStrategy(
                    scope = context.scope,
                    addressFieldState = context.addressFieldState,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    fiatAmountFieldState = context.fiatAmountFieldState,
                    memoFieldState = context.memoFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    transactionRepository = transactionRepository,
                    bitcoinPlanService = context.bitcoinPlanService,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    chainValidationService = chainValidationService,
                    addressManager = context.addressManager,
                    amountManager = context.amountManager,
                    gasSettings = context.gasSettings,
                    planBtc = context.planBtc,
                    planFee = context.planFee,
                    accounts = context.accounts,
                    appCurrency = context.appCurrency,
                    vaultIdProvider = context.vaultIdProvider,
                    selectedAccountProvider = context.selectedAccountProvider,
                    defiTypeProvider = context.defiTypeProvider,
                    currentTronFrozenBalanceProvider = context.currentTronFrozenBalanceProvider,
                    navigator = navigator,
                    expandSection = context.expandSection,
                    emitFocusField = context.emitFocusField,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            bond =
                BondStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    providerBondFieldState = context.providerBondFieldState,
                    operatorFeesBondFieldState = context.operatorFeesBondFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    addressParserRepository = addressParserRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            unbond =
                UnbondStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    providerBondFieldState = context.providerBondFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    addressParserRepository = addressParserRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            stake =
                StakeStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = context.defiTypeProvider,
                    isAutocompoundProvider = context.isAutocompoundProvider,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            unstake =
                UnstakeStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = context.defiTypeProvider,
                    isAutocompoundProvider = context.isAutocompoundProvider,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            mint =
                MintStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = context.defiTypeProvider,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            redeem =
                RedeemStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    slippageFieldState = context.slippageFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    chainValidationService = chainValidationService,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = context.defiTypeProvider,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
            withdrawUsdcCircle =
                WithdrawUsdcCircleStrategy(
                    scope = context.scope,
                    tokenAmountFieldState = context.tokenAmountFieldState,
                    accountValidator = context.accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    mscaAddressProvider = context.mscaAddressProvider,
                    showLoading = context.showLoading,
                    hideLoading = context.hideLoading,
                    showError = context.showError,
                ),
        )
}
