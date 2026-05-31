@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.AccountValidator
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.models.send.submit.SendStrategies
import com.vultisig.wallet.ui.models.send.submit.SendStrategyContext
import com.vultisig.wallet.ui.models.send.submit.SendStrategyFactory
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import wallet.core.jni.proto.Bitcoin

/**
 * Owns the construction and wiring of every [SendFormViewModel] collaborator.
 *
 * Collaborators are declared as `by lazy` so the dependency graph is resolved on first access
 * rather than by property-declaration order — reordering declarations can no longer silently NPE
 * the way it could when this wiring lived inline in the ViewModel body.
 *
 * The ViewModel passes in the shared flows it owns (e.g. [uiState], [selectedToken]) plus
 * live-bound providers and callbacks that close over its mutable state ([vaultProvider],
 * [expandSection], ...), so collaborators always read current ViewModel state rather than a
 * construction-time snapshot.
 */
@ExperimentalStdlibApi
internal class SendFormGraph(
    private val scope: CoroutineScope,
    private val navigator: Navigator<Destination>,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val requestResultRepository: RequestResultRepository,
    private val addressParserRepository: AddressParserRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val advanceGasUiRepository: AdvanceGasUiRepository,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val chainValidationService: ChainValidationService,
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase,
    private val sendStrategyFactory: SendStrategyFactory,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val uiState: MutableStateFlow<SendFormUiModel>,
    private val selectedToken: MutableStateFlow<Coin?>,
    private val accountsState: MutableStateFlow<AccountsLoadState>,
    private val accounts: StateFlow<List<Account>>,
    private val appCurrency: StateFlow<AppCurrency>,
    private val addressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val fiatAmountFieldState: TextFieldState,
    private val memoFieldState: TextFieldState,
    private val operatorFeesBondFieldState: TextFieldState,
    private val providerBondFieldState: TextFieldState,
    private val slippageFieldState: TextFieldState,
    private val vaultProvider: () -> Vault?,
    private val vaultIdProvider: () -> VaultId?,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val mscaAddressProvider: () -> String?,
    private val selectedTokenProvider: () -> Coin?,
    private val selectedAccountProvider: () -> Account?,
    private val isAutocompoundProvider: () -> Boolean,
    private val expandSection: (SendSections) -> Unit,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (UiText) -> Unit,
    private val emitFocusField: (SendFocusField) -> Unit,
) {
    private val planFee = MutableStateFlow<Long?>(null)
    private val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val tronFrozenBalances =
        MutableStateFlow<TronFrozenBalanceState>(TronFrozenBalanceState.Loading)

    /** Pending advanced gas settings; read and updated by the ViewModel's gas settings handlers. */
    val gasSettings = MutableStateFlow<GasSettings?>(null)

    /** Chain-specific tx data (incl. UTXO inputs); read and patched by the ViewModel. */
    val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)

    /** Gates account-dependent UI while a data-source switch (e.g. autocompound) is in flight. */
    val isSwitchingAccounts = MutableStateFlow(false)

    /** Preselects a token/network once accounts load, honoring deep-link args. */
    val tokenPreselectionService: TokenPreselectionService by lazy {
        TokenPreselectionService(
            scope = scope,
            accountsState = accountsState,
            defiTypeProvider = defiTypeProvider,
            selectedTokenProvider = selectedTokenProvider,
            onTokenSelected = { tokenNetworkSelectionDelegate.selectToken(it) },
        )
    }

    /** Loads vault accounts (and staking variants) into [accountsState]. */
    val accountsLoader: AccountsLoader by lazy {
        AccountsLoader(
            scope = scope,
            accountsState = accountsState,
            accountsRepository = accountsRepository,
            stakingDetailsRepository = stakingDetailsRepository,
            defiTypeProvider = defiTypeProvider,
            mscaAddressProvider = mscaAddressProvider,
        )
    }

    /** Drives the TRON freeze/unfreeze (staking) form state. */
    val tronStakingService: TronStakingService by lazy {
        TronStakingService(
            scope = scope,
            uiState = uiState,
            tronFrozenBalances = tronFrozenBalances,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            defiTypeProvider = defiTypeProvider,
            vaultProvider = vaultProvider,
            vaultIdProvider = vaultIdProvider,
            vaultRepository = vaultRepository,
            getTronFrozenBalances = getTronFrozenBalances,
        )
    }

    /** Resolves and validates the destination address field. */
    val addressManager: AddressManager by lazy {
        AddressManager(
            scope = scope,
            addressFieldState = addressFieldState,
            selectedToken = selectedToken,
            chainAccountAddressRepository = chainAccountAddressRepository,
            addressParserRepository = addressParserRepository,
        )
    }

    /** Keeps token and fiat amount fields in sync and validates amounts. */
    val amountManager: AmountManager by lazy {
        AmountManager(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            selectedToken = selectedToken,
            gasFee = gasFee,
            accountProvider = selectedAccountProvider,
            appCurrency = appCurrency,
            chainValidationService = chainValidationService,
            tokenPriceRepository = tokenPriceRepository,
        )
    }

    /** Validates the full form prior to submission. */
    val accountValidator: AccountValidator by lazy {
        AccountValidator(
            vaultIdProvider = vaultIdProvider,
            selectedAccountProvider = selectedAccountProvider,
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            gasFee = gasFee,
            addressParserRepository = addressParserRepository,
        )
    }

    /** Builds and caches the Bitcoin spend plan. */
    val bitcoinPlanService: BitcoinPlanService by lazy { BitcoinPlanService(vaultRepository) }

    /** Estimates and refreshes the gas/network fee. */
    val gasFeeOrchestrator: GasFeeOrchestrator by lazy {
        GasFeeOrchestrator(
            scope = scope,
            uiState = uiState,
            selectedToken = selectedToken,
            accounts = accounts,
            gasFee = gasFee,
            gasSettings = gasSettings,
            specific = specific,
            planFee = planFee,
            planBtc = planBtc,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            memoFieldState = memoFieldState,
            vaultProvider = vaultProvider,
            vaultIdProvider = vaultIdProvider,
            accountProvider = selectedAccountProvider,
            resolvedDstAddressProvider = { addressManager.resolvedDstAddress.value },
            isMaxAmountFlow = amountManager.isMaxAmount,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            addressParserRepository = addressParserRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            advanceGasUiRepository = advanceGasUiRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            bitcoinPlanService = bitcoinPlanService,
            mapTokenValueToString = mapTokenValueToString,
        )
    }

    /** Computes max/percentage amounts accounting for gas and reserves. */
    val amountFractionManager: AmountFractionManager by lazy {
        AmountFractionManager(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            memoFieldState = memoFieldState,
            uiState = uiState,
            gasFee = gasFee,
            gasSettings = gasSettings,
            specific = specific,
            defiTypeProvider = defiTypeProvider,
            vaultProvider = vaultProvider,
            accountProvider = selectedAccountProvider,
            currentTronFrozenBalanceProvider = tronStakingService::currentFrozenBalance,
            getAvailableTokenBalance = getAvailableTokenBalance,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            adjustGasFee = ::adjustGasFee,
            amountManager = amountManager,
        )
    }

    /** Handles token and network selection, including the selection popup. */
    val tokenNetworkSelectionDelegate: TokenNetworkSelectionDelegate by lazy {
        TokenNetworkSelectionDelegate(
            scope = scope,
            navigator = navigator,
            requestResultRepository = requestResultRepository,
            tokenRepository = tokenRepository,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenPreselectionService = tokenPreselectionService,
            accountsLoader = accountsLoader,
            amountFractionManager = amountFractionManager,
            amountManager = amountManager,
            vaultIdProvider = vaultIdProvider,
            accounts = accounts,
            selectedToken = selectedToken,
            expandSection = expandSection,
        )
    }

    /** The per-DeFi-action submit strategies (bond, stake, mint, ...). */
    val strategies: SendStrategies by lazy {
        sendStrategyFactory.create(
            SendStrategyContext(
                scope = scope,
                addressFieldState = addressFieldState,
                tokenAmountFieldState = tokenAmountFieldState,
                fiatAmountFieldState = fiatAmountFieldState,
                memoFieldState = memoFieldState,
                slippageFieldState = slippageFieldState,
                operatorFeesBondFieldState = operatorFeesBondFieldState,
                providerBondFieldState = providerBondFieldState,
                accountValidator = accountValidator,
                bitcoinPlanService = bitcoinPlanService,
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
                isAutocompoundProvider = isAutocompoundProvider,
                mscaAddressProvider = mscaAddressProvider,
                currentTronFrozenBalanceProvider = tronStakingService::currentFrozenBalance,
                expandSection = expandSection,
                emitFocusField = emitFocusField,
                showLoading = showLoading,
                hideLoading = hideLoading,
                showError = showError,
            )
        )
    }
}
