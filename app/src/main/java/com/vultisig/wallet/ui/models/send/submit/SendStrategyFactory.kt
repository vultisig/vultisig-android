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
     * Wires the per-instance ViewModel state (scope, field states, flows, callbacks) with the
     * shared dependencies and returns the eight strategies.
     */
    fun create(
        scope: CoroutineScope,
        addressFieldState: TextFieldState,
        tokenAmountFieldState: TextFieldState,
        fiatAmountFieldState: TextFieldState,
        memoFieldState: TextFieldState,
        slippageFieldState: TextFieldState,
        operatorFeesBondFieldState: TextFieldState,
        providerBondFieldState: TextFieldState,
        accountValidator: AccountValidator,
        bitcoinPlanService: BitcoinPlanService,
        addressManager: AddressManager,
        amountManager: AmountManager,
        gasSettings: StateFlow<GasSettings?>,
        planBtc: MutableStateFlow<Bitcoin.TransactionPlan?>,
        planFee: MutableStateFlow<Long?>,
        accounts: StateFlow<List<Account>>,
        appCurrency: StateFlow<AppCurrency>,
        vaultIdProvider: () -> String?,
        selectedAccountProvider: () -> Account?,
        defiTypeProvider: () -> DeFiNavActions?,
        isAutocompoundProvider: () -> Boolean,
        mscaAddressProvider: () -> String?,
        currentTronFrozenBalanceProvider: () -> BigDecimal?,
        expandSection: (SendSections) -> Unit,
        emitFocusField: (SendFocusField) -> Unit,
        showLoading: () -> Unit,
        hideLoading: () -> Unit,
        showError: (UiText) -> Unit,
    ): SendStrategies =
        SendStrategies(
            default =
                DefaultSendStrategy(
                    scope = scope,
                    addressFieldState = addressFieldState,
                    tokenAmountFieldState = tokenAmountFieldState,
                    fiatAmountFieldState = fiatAmountFieldState,
                    memoFieldState = memoFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    transactionRepository = transactionRepository,
                    bitcoinPlanService = bitcoinPlanService,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    chainValidationService = chainValidationService,
                    addressManager = addressManager,
                    amountManager = amountManager,
                    gasSettings = gasSettings,
                    planBtc = planBtc,
                    planFee = planFee,
                    accounts = accounts,
                    appCurrency = appCurrency,
                    vaultIdProvider = vaultIdProvider,
                    selectedAccountProvider = selectedAccountProvider,
                    defiTypeProvider = defiTypeProvider,
                    currentTronFrozenBalanceProvider = currentTronFrozenBalanceProvider,
                    navigator = navigator,
                    expandSection = expandSection,
                    emitFocusField = emitFocusField,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            bond =
                BondStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    providerBondFieldState = providerBondFieldState,
                    operatorFeesBondFieldState = operatorFeesBondFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    addressParserRepository = addressParserRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            unbond =
                UnbondStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    providerBondFieldState = providerBondFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    addressParserRepository = addressParserRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            stake =
                StakeStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = defiTypeProvider,
                    isAutocompoundProvider = isAutocompoundProvider,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            unstake =
                UnstakeStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = defiTypeProvider,
                    isAutocompoundProvider = isAutocompoundProvider,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            mint =
                MintStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = defiTypeProvider,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            redeem =
                RedeemStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    slippageFieldState = slippageFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    chainValidationService = chainValidationService,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    defiTypeProvider = defiTypeProvider,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
            withdrawUsdcCircle =
                WithdrawUsdcCircleStrategy(
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    accountValidator = accountValidator,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    accountsRepository = accountsRepository,
                    blockChainSpecificRepository = blockChainSpecificRepository,
                    getAvailableTokenBalance = getAvailableTokenBalance,
                    gasFeeToEstimatedFee = gasFeeToEstimatedFee,
                    depositTransactionRepository = depositTransactionRepository,
                    navigator = navigator,
                    mscaAddressProvider = mscaAddressProvider,
                    showLoading = showLoading,
                    hideLoading = hideLoading,
                    showError = showError,
                ),
        )
}
