@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
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

@ExperimentalStdlibApi
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
    private val feeServiceComposite: FeeServiceComposite,
    private val chainValidationService: ChainValidationService,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase,
    private val sendStrategyFactory: SendStrategyFactory,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Send>()

    val uiState = MutableStateFlow(SendFormUiModel())

    private val _focusFieldChannel = Channel<SendFocusField>(Channel.BUFFERED)
    val focusFieldFlow = _focusFieldChannel.receiveAsFlow()

    val addressFieldState = TextFieldState()
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val memoFieldState = TextFieldState()

    // bond node
    val operatorFeesBondFieldState = TextFieldState()
    val providerBondFieldState = TextFieldState()

    // Trade
    val slippageFieldState = TextFieldState()

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
            feeServiceComposite = feeServiceComposite,
            chainValidationService = chainValidationService,
            getTronFrozenBalances = getTronFrozenBalances,
            sendStrategyFactory = sendStrategyFactory,
            mapTokenValueToString = mapTokenValueToString,
            accountToTokenBalanceUiModelMapper = accountToTokenBalanceUiModelMapper,
            requestAddressBookEntry = requestAddressBookEntry,
            uiState = uiState,
            selectedToken = selectedToken,
            accountsState = accountsState,
            accounts = accounts,
            appCurrency = appCurrency,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            operatorFeesBondFieldState = operatorFeesBondFieldState,
            providerBondFieldState = providerBondFieldState,
            slippageFieldState = slippageFieldState,
            selectedTokenProvider = { selectedTokenValue },
            selectedAccountProvider = { selectedAccount },
            isAutocompoundProvider = { uiState.value.isAutocompound },
            expandSection = ::expandSection,
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
            emitFocusField = { field -> _focusFieldChannel.trySend(field) },
        )

    init {
        graph.initialize(args)
        graph.gasFeeOrchestrator.start()
        graph.addressManager.start()
        graph.amountManager.start()
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

    fun selectNetwork() = graph.tokenNetworkSelectionDelegate.selectNetwork()

    fun onNetworkLongPressStarted(position: Offset) =
        graph.tokenNetworkSelectionDelegate.onNetworkLongPressStarted(position)

    fun openTokenSelection() = graph.tokenNetworkSelectionDelegate.openTokenSelection()

    fun openTokenSelectionPopup(position: Offset) =
        graph.tokenNetworkSelectionDelegate.openTokenSelectionPopup(position)

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

    fun setProviderAddress(address: String) {
        providerBondFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scanAddress() {
        viewModelScope.safeLaunch {
            val qr = requestQrScan.invoke()
            if (!qr.isNullOrBlank()) {
                setAddressFromQrCode(qr, null, null)
            }
        }
    }

    fun scanProviderAddress() {
        viewModelScope.safeLaunch {
            val qr = requestQrScan.invoke()
            if (!qr.isNullOrBlank()) {
                setAddressFromQrCode(qr, null, null, providerBondFieldState)
            }
        }
    }

    fun onAutoCompound(checked: Boolean) =
        graph.tokenNetworkSelectionDelegate.onAutoCompound(checked)

    fun openAddressBook(addressType: AddressBookType = AddressBookType.OUTPUT) =
        graph.addressManager.openAddressBook(addressType)

    fun dismissGasSettings() {
        advanceGasUiRepository.hideSettings()
    }

    fun saveGasSettings(settings: GasSettings) {
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

    fun onClickContinue() = graph.strategies.submitFor(uiState.value.defiType)

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
        uiState.update { it.copy(expandedSection = section) }
    }

    fun refreshGasFee() = graph.gasFeeOrchestrator.refresh()
}
