package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.DefaultDispatcher
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.SendStrategyFactory
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class SendFormViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val requestQrScan: RequestQrScanUseCase,
    private val accountsRepository: AccountsRepository,
    appCurrencyRepository: AppCurrencyRepository,
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
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val rujiStakingService: RujiStakingService,
    private val feeServiceComposite: FeeServiceComposite,
    private val chainValidationService: ChainValidationService,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val enableToken: EnableTokenUseCase,
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase,
    private val sendStrategyFactory: SendStrategyFactory,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Send>()

    val uiState = MutableStateFlow(SendFormUiModel())

    private val _focusFieldChannel = Channel<SendFocusField>(Channel.BUFFERED)
    val focusFieldFlow = _focusFieldChannel.receiveAsFlow()

    val addressFieldState = TextFieldState()
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val memoFieldState = TextFieldState()

    // XRP-only destination tag (shown below the memo field).
    val destinationTagFieldState = TextFieldState()

    // Trade
    val slippageFieldState = TextFieldState()

    // Plain-send asset step search
    val assetSearchFieldState = TextFieldState()

    private val selectedToken = MutableStateFlow<Coin?>(null)

    private val selectedTokenValue: Coin?
        get() = selectedToken.value

    private val accountsState = MutableStateFlow<AccountsLoadState>(AccountsLoadState.Uninitialized)

    // Filter out Uninitialized so reload transitions (vault switch, mscaChanged,
    // autocompound toggle) don't flash `accounts` to emptyList() — that flash would
    // transiently clear the gas fee, fail validation in accountValidator, and render a
    // placeholder row in collectSelectedAccount. Downstream consumers here only care
    // about the actual list and keep the prior Loaded(...) value until the new one
    // arrives. TokenPreselectionService still consumes accountsState directly and
    // correctly observes the Uninitialized reset.
    private val accounts: StateFlow<List<Account>> =
        accountsState
            .filterIsInstance<AccountsLoadState.Loaded>()
            .map { it.accounts }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedAccount: Account?
        get() {
            val selectedTokenValue = selectedTokenValue
            val accounts = accounts.value
            return accounts.find { it.token.id.equals(selectedTokenValue?.id, true) }
        }

    private val appCurrency =
        appCurrencyRepository.currency.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    private val graph =
        SendFormGraph(
            scope = viewModelScope,
            navigator = navigator,
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenPriceRepository = tokenPriceRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            requestResultRepository = requestResultRepository,
            addressParserRepository = addressParserRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            advanceGasUiRepository = advanceGasUiRepository,
            vaultRepository = vaultRepository,
            tokenRepository = tokenRepository,
            stakingDetailsRepository = stakingDetailsRepository,
            defaultStakingPositionService = defaultStakingPositionService,
            rujiStakingService = rujiStakingService,
            feeServiceComposite = feeServiceComposite,
            chainValidationService = chainValidationService,
            getTronFrozenBalances = getTronFrozenBalances,
            sendStrategyFactory = sendStrategyFactory,
            mapTokenValueToString = mapTokenValueToString,
            accountToTokenBalanceUiModelMapper = accountToTokenBalanceUiModelMapper,
            requestAddressBookEntry = requestAddressBookEntry,
            enableToken = enableToken,
            uiState = uiState,
            selectedToken = selectedToken,
            accountsState = accountsState,
            accounts = accounts,
            appCurrency = appCurrency,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            destinationTagFieldState = destinationTagFieldState,
            slippageFieldState = slippageFieldState,
            assetSearchFieldState = assetSearchFieldState,
            listingDispatcher = defaultDispatcher,
            selectedTokenProvider = { selectedTokenValue },
            selectedAccountProvider = { selectedAccount },
            isAutocompoundProvider = { uiState.value.isAutocompound },
            expandSection = ::expandSection,
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
            emitFocusField = ::requestFieldFocus,
        )

    init {
        graph.initialize(args)
        graph.gasFeeOrchestrator.start()
        graph.addressManager.start()
        graph.amountManager.start()
        graph.amountFractionManager.start()
        graph.bindUiState()
    }

    fun setTronResourceType(type: TronResourceType) {
        // Preempt any in-flight percentage calc so it can't resume after the field clears
        // and overwrite them with the amount computed for the previous resource type.
        graph.amountFractionManager.cancel()
        graph.tronStakingService.setResourceType(type)
    }

    fun validateTokenAmount() {
        val errorText =
            graph.amountManager.validateTokenAmount(tokenAmountFieldState.text.toString())
        uiState.update { it.copy(tokenAmountError = errorText) }
    }

    /**
     * Asset step chip: shows [chain] (null = every chain); a search ignores chips, so it clears.
     */
    fun selectAssetChainFilter(chain: Chain?) {
        assetSearchFieldState.clearText()
        uiState.update { it.copy(assetChainFilter = chain) }
    }

    /**
     * Asset step row: selects [token] and moves on to Address, also for the coin already chosen.
     */
    fun selectAsset(token: Coin) {
        if (!token.id.equals(selectedTokenValue?.id, ignoreCase = true)) {
            graph.tokenNetworkSelectionDelegate.selectToken(token)
        }
        expandSection(SendSections.Address)
    }

    /**
     * Asset step empty search: adds a custom token on the chain chip picked and searches for it.
     */
    fun addCustomToken() {
        val chain = uiState.value.assetChainFilter ?: return
        graph.tokenNetworkSelectionDelegate.addCustomToken(chain) { token ->
            assetSearchFieldState.setTextAndPlaceCursorAtEnd(token.ticker)
        }
    }

    fun openGasSettings() {
        viewModelScope.launch { advanceGasUiRepository.showSettings() }
    }

    fun setAddressFromQrCode(
        qrCode: String?,
        preSelectedChainId: ChainId?,
        preSelectedTokenId: TokenId?,
        fieldState: TextFieldState = addressFieldState,
    ) =
        graph.tokenNetworkSelectionDelegate.setAddressFromQrCode(
            qrCode,
            preSelectedChainId,
            preSelectedTokenId,
            fieldState,
        )

    fun setOutputAddress(address: String) {
        graph.addressManager.setOutputAddress(address)
    }

    fun scanAddress() {
        viewModelScope.safeLaunch {
            val qr = requestQrScan.invoke()
            if (!qr.isNullOrBlank()) {
                setAddressFromQrCode(qr, null, null)
            }
        }
    }

    fun onAutoCompound(checked: Boolean) =
        graph.tokenNetworkSelectionDelegate.onAutoCompound(checked)

    fun openAddressBook() = graph.addressManager.openAddressBook()

    fun dismissGasSettings() {
        advanceGasUiRepository.hideSettings()
    }

    // The chain the saved settings belong to, so another chain's sheet never shows them.
    private var gasSettingsChain: Chain? = null

    /** The advanced gas settings last saved for [chain], for the sheet to show again. */
    fun savedGasSettings(chain: Chain): GasSettings? =
        graph.gasSettings.value.takeIf { gasSettingsChain == chain }

    fun saveGasSettings(settings: GasSettings) {
        gasSettingsChain = uiState.value.selectedCoin?.model?.address?.chain
        graph.gasSettings.value = settings
        if (settings is GasSettings.UTXO) {
            val currentSpec = graph.specific.value ?: return
            val utxoSpec = currentSpec.blockChainSpecific as? BlockChainSpecific.UTXO ?: return
            graph.specific.value =
                currentSpec.copy(blockChainSpecific = utxoSpec.copy(byteFee = settings.byteFee))
        }
    }

    fun chooseMaxTokenAmount() = graph.amountFractionManager.chooseMaxTokenAmount()

    fun choosePercentageAmount(amountFraction: AmountFraction) =
        graph.amountFractionManager.choosePercentageAmount(amountFraction)

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    /**
     * Continue: DeFi forms submit; a plain send moves Asset → Address → Amount and submits from
     * Amount. Address only moves on once the recipient is valid, so its inline error stays in view.
     */
    fun onClickContinue() {
        val state = uiState.value
        if (state.defiType != null) {
            graph.strategies.submitFor(state.defiType)
            return
        }
        when (state.expandedSection) {
            SendSections.Asset ->
                if (state.isSelectedAssetListed) expandSection(SendSections.Address)
            SendSections.Address ->
                when {
                    state.isDstAddressValid -> expandSection(SendSections.Amount)
                    addressFieldState.text.isBlank() -> requestFieldFocus(SendFocusField.ADDRESS)
                }
            SendSections.Amount -> graph.strategies.submitFor(null)
        }
    }

    /**
     * Moves focus to [field]. A plain send composes only the step on screen, so the request waits
     * in [SendFormUiModel.pendingFocusField] for the step that owns the field; DeFi forms focus at
     * once.
     */
    private fun requestFieldFocus(field: SendFocusField) {
        if (uiState.value.defiType == null) {
            uiState.update { it.copy(pendingFocusField = field) }
        } else {
            _focusFieldChannel.trySend(field)
        }
    }

    /** The step owning [SendFormUiModel.pendingFocusField] has focused it. */
    fun onPendingFocusApplied() {
        uiState.update { it.copy(pendingFocusField = null) }
    }

    /** Header tap: jumps to [step] when every earlier step is complete. */
    fun selectStep(step: SendSections) {
        if (uiState.value.canNavigate(step)) expandSection(step)
    }

    private fun hideLoading() {
        uiState.update { it.copy(isLoading = false) }
    }

    private fun showLoading() {
        uiState.update { it.copy(isLoading = true) }
    }

    private fun showError(text: UiText) {
        uiState.update { it.copy(errorText = text) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun toggleAmountInputType(usingTokenAmountInput: Boolean) {
        uiState.update { it.copy(usingTokenAmountInput = usingTokenAmountInput) }
    }

    fun expandSection(section: SendSections) {
        uiState.update {
            it.copy(
                expandedSection = section,
                isAssetStepComplete = it.isAssetStepComplete || section != SendSections.Asset,
            )
        }
    }

    fun refreshGasFee() = graph.gasFeeOrchestrator.refresh()
}
