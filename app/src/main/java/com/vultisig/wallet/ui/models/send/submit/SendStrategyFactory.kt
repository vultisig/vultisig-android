package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
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
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin

/**
 * Bundle of the six submit strategies produced by [SendStrategyFactory.create] for a single
 * `SendFormViewModel` instance.
 */
internal data class SendStrategies(
    val default: DefaultSendStrategy,
    val stake: StakeStrategy,
    val unstake: UnstakeStrategy,
    val mint: MintStrategy,
    val redeem: RedeemStrategy,
    val withdrawUsdcCircle: WithdrawUsdcCircleStrategy,
    // Guards a Route.Send that somehow still carries a Deposit-only defiType (a stale deep link or
    // restored back stack): falls back to DefaultSendStrategy would silently submit an ordinary
    // transfer instead of the Bond/Unbond/etc. memo the user expects.
    val onUnsupportedDefiType: (DeFiNavActions) -> Unit,
) {

    /**
     * Submits the form using the strategy that matches [defiType].
     *
     * @param defiType the active DeFi action, or `null` for a plain send.
     */
    fun submitFor(defiType: DeFiNavActions?) {
        when (defiType) {
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_SRUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY,
            DeFiNavActions.STAKE_YBRUNE -> stake.submit()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_SRUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.UNSTAKE_YBRUNE,
            DeFiNavActions.WITHDRAW_RUJI -> unstake.submit()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> mint.submit()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> redeem.submit()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> withdrawUsdcCircle.submit()

            // Bond/Unbond/Stake-Cacao/Unstake-Cacao/Remove-LP only submit through the Deposit
            // flow now — fail closed rather than falling back to a plain send.
            DeFiNavActions.BOND,
            DeFiNavActions.UNBOND,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.REMOVE_LP -> onUnsupportedDefiType(defiType)

            null,
            // ADD_LP still submits through the Send flow for the EVM asset side of a pool add,
            // which the Deposit flow's AddLiquidityStrategy doesn't cover (RUNE/CACAO side only).
            DeFiNavActions.ADD_LP,
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX,
            // TON staking submits through the Deposit flow, not the Send flow.
            DeFiNavActions.STAKE_TON,
            DeFiNavActions.UNSTAKE_TON -> default.submit()
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
    val destinationTagFieldState: TextFieldState,
    val slippageFieldState: TextFieldState,
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
    private val chainValidationService: ChainValidationService,
    private val navigator: Navigator<Destination>,
    private val thorChainApi: ThorChainApi,
    private val defaultStakingPositionService: DefaultStakingPositionService,
) {

    /**
     * Wires the per-instance ViewModel state in [context] with the shared dependencies and returns
     * the six strategies.
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
                    destinationTagFieldState = context.destinationTagFieldState,
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
                    thorChainApi = thorChainApi,
                    defaultStakingPositionService = defaultStakingPositionService,
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
            onUnsupportedDefiType = { defiType ->
                Timber.e(
                    "Route.Send received a Deposit-only defiType (%s); refusing to submit it as a" +
                        " plain send",
                    defiType,
                )
                context.showError(UiText.StringResource(R.string.dialog_default_error_body))
            },
        )
}
