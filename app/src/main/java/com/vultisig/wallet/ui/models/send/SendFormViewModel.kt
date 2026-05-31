@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
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
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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

    private var vault: Vault? = null
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

    private var vaultId: String? = null

    private var defiType: DeFiNavActions? = null // Default is send, no defi form

    private var mscaAddress: String? = null

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
            vaultProvider = { vault },
            vaultIdProvider = { vaultId },
            defiTypeProvider = { defiType },
            mscaAddressProvider = { mscaAddress },
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
        loadData(
            vaultId = args.vaultId,
            preSelectedChainId = args.chainId,
            preSelectedTokenId = args.tokenId,
            address = args.address,
            amount = args.amount,
            memo = args.memo,
            type = args.type,
            mscaAddress = args.mscaAddress,
        )
        loadSelectedCurrency()
        collectSelectedAccount()
        graph.gasFeeOrchestrator.start()
        collectAdvanceGasUi()
        loadGasSettings()
        graph.addressManager.start()
        graph.amountManager.start()
        observeManagers()
    }

    private fun observeManagers() {
        viewModelScope.launch {
            graph.addressManager.isDstAddressComplete.collect { isComplete ->
                uiState.update { it.copy(isDstAddressComplete = isComplete) }
            }
        }
        viewModelScope.launch {
            graph.addressManager.onAddressValidated.collect { expandSection(SendSections.Amount) }
        }
        viewModelScope.launch {
            graph.amountManager.reapingError.collect { error ->
                uiState.update { it.copy(reapingError = error) }
            }
        }
    }

    private fun loadGasSettings() {
        viewModelScope.launch {
            advanceGasUiRepository.shouldShowAdvanceGasSettingsIcon.collect {
                shouldShowAdvanceGasSettingsIcon ->
                uiState.update { it.copy(hasGasSettings = shouldShowAdvanceGasSettingsIcon) }
            }
        }
    }

    fun loadData(
        vaultId: VaultId,
        preSelectedChainId: ChainId?,
        preSelectedTokenId: TokenId?,
        address: String?,
        amount: String?,
        memo: String?,
        type: String?,
        mscaAddress: String?,
    ) {
        // Clear all optional form fields so values from a prior loadData() don't
        // leak when the corresponding arg is absent on a subsequent invocation.
        addressFieldState.clearText()
        tokenAmountFieldState.clearText()
        memoFieldState.clearText()
        slippageFieldState.clearText()
        this.defiType =
            if (type == null) {
                null
            } else {
                parseDepositType(type)
            }

        this.mscaAddress = mscaAddress
        this.vaultId = vaultId
        graph.accountsLoader.load(vaultId)
        loadVaultName()
        initFormType()

        if (address != null) {
            setAddressFromQrCode(
                qrCode = address,
                preSelectedChainId = preSelectedChainId,
                preSelectedTokenId = preSelectedTokenId,
            )
        } else {
            graph.tokenPreselectionService.preSelect(
                preSelectedChainIds = listOf(preSelectedChainId),
                preSelectedTokenId = preSelectedTokenId,
            )
        }

        if (preSelectedTokenId != null && address == null) {
            expandSection(SendSections.Address)
        }

        if (preSelectedTokenId != null && address != null) {
            expandSection(SendSections.Amount)
        }

        amount?.let { tokenAmountFieldState.setTextAndPlaceCursorAtEnd(it) }

        memo?.let { memoFieldState.setTextAndPlaceCursorAtEnd(it) }

        if (defiType == DeFiNavActions.REDEEM_YRUNE || defiType == DeFiNavActions.REDEEM_YTCY) {
            slippageFieldState.setTextAndPlaceCursorAtEnd("1.0")
        }
    }

    private fun initFormType() {
        val autoCompound =
            defiType == DeFiNavActions.STAKE_STCY || defiType == DeFiNavActions.UNSTAKE_STCY
        val initialResourceType =
            if (graph.tronStakingService.isStakingType()) TronResourceType.BANDWIDTH else null
        uiState.update {
            it.copy(
                defiType = this.defiType,
                isAutocompound = autoCompound,
                tronResourceType = initialResourceType,
            )
        }
        graph.tronStakingService.initIfStakingType()
    }

    fun setTronResourceType(type: TronResourceType) {
        // Preempt any in-flight percentage calc so it can't resume after the field clears
        // and overwrite them with the amount computed for the previous resource type.
        graph.amountFractionManager.cancel()
        graph.tronStakingService.setResourceType(type)
    }

    private fun loadVaultName() {
        viewModelScope.safeLaunch {
            val requestedVaultId = vaultId ?: return@safeLaunch
            val vault = vaultRepository.get(requestedVaultId) ?: return@safeLaunch
            // Drop stale completions: a slower fetch for a previous vault must not
            // overwrite state after a newer loadData() switched the vaultId.
            if (this@SendFormViewModel.vaultId != requestedVaultId) return@safeLaunch

            this@SendFormViewModel.vault = vault
            uiState.update { it.copy(srcVaultName = vault.name) }
        }
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
    ) {
        if (!qrCode.isNullOrBlank()) {
            Timber.d("setAddressFromQrCode(address = $qrCode)")

            fieldState.setTextAndPlaceCursorAtEnd(qrCode)

            val vaultId = vaultId
            if (!vaultId.isNullOrBlank()) {
                val chainValidForAddress =
                    preSelectedChainId?.let { listOf(Chain.fromRaw(preSelectedChainId)) }
                        ?: Chain.entries.filter { chain ->
                            chainAccountAddressRepository.isValid(chain, qrCode)
                        }

                val selectedChain = selectedTokenValue?.chain

                if (
                    chainValidForAddress.isNotEmpty() &&
                        !chainValidForAddress.contains(selectedChain)
                ) {
                    Timber.d(
                        "Address from QR has a different chain " +
                            "than selected token, switching. $chainValidForAddress != $selectedChain"
                    )
                    val preSelectedChainIds = chainValidForAddress.map { it.id }

                    graph.tokenNetworkSelectionDelegate.checkChainIdExistInAccounts(
                        preSelectedChainIds = preSelectedChainIds,
                        vaultId = vaultId,
                    )

                    graph.tokenPreselectionService.preSelect(
                        preSelectedChainIds = preSelectedChainIds,
                        preSelectedTokenId = preSelectedTokenId,
                        forcePreselection = true,
                    )
                }
            }
        }
    }

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

    fun onAutoCompound(checked: Boolean) {
        uiState.update { it.copy(isAutocompound = checked) }

        val vaultId = vaultId
        if (
            (defiType != DeFiNavActions.UNSTAKE_TCY && defiType != DeFiNavActions.UNSTAKE_STCY) ||
                vaultId == null
        ) {
            return
        }

        graph.isSwitchingAccounts.value = true
        selectedToken.value = null

        // Route the data-source switch through AccountsLoader so accountsState has a single
        // writer — the previous in-VM collect raced against the already-running loader,
        // and for UNSTAKE_TCY the two collectors pulled from different APIs. The callback
        // runs per emission so we can pin the token selection on the first emission
        // containing the target ticker (the tokenSelected flag prevents a later hydration
        // emission from re-calling selectToken, which would wipe the user's typed amount).
        val targetTicker = if (checked) "sTCY" else "TCY"
        var tokenSelected = false
        graph.accountsLoader.loadForAutoCompoundSwitch(
            vaultId = vaultId,
            useStableCompound = checked,
        ) { loadedAccounts ->
            // Release the gate on every emission — if the target ticker is never found the
            // form must still become interactive rather than staying gated forever. Setting
            // to false repeatedly is a no-op once already false.
            graph.isSwitchingAccounts.value = false
            if (!tokenSelected) {
                loadedAccounts
                    .find {
                        it.token.ticker.equals(targetTicker, true) &&
                            it.token.chain == Chain.ThorChain
                    }
                    ?.let {
                        tokenSelected = true
                        graph.tokenNetworkSelectionDelegate.selectToken(it.token)
                    }
            }
        }
    }

    fun openAddressBook(addressType: AddressBookType = AddressBookType.OUTPUT) {
        viewModelScope.safeLaunch {
            val vaultId = vaultId ?: return@safeLaunch
            val selectedChain = selectedTokenValue?.chain ?: return@safeLaunch

            val address: AddressBookEntry =
                requestAddressBookEntry(chainId = selectedChain.id, excludeVaultId = vaultId)
                    ?: return@safeLaunch

            when (addressType) {
                AddressBookType.OUTPUT -> {
                    val selectedNewChain = address.chain
                    graph.tokenNetworkSelectionDelegate.checkIfTokenSelectionRequired(
                        currentChain = selectedChain,
                        newChain = selectedNewChain,
                    )
                    setOutputAddress(address.address)
                }

                AddressBookType.PROVIDER -> {
                    setProviderAddress(address.address)
                }
            }
        }
    }

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

    fun onClickContinue() {
        when (uiState.value.defiType) {
            DeFiNavActions.BOND -> graph.strategies.bond.submit()
            DeFiNavActions.UNBOND -> graph.strategies.unbond.submit()
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY -> graph.strategies.stake.submit()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.WITHDRAW_RUJI -> graph.strategies.unstake.submit()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> graph.strategies.mint.submit()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> graph.strategies.redeem.submit()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> graph.strategies.withdrawUsdcCircle.submit()

            null,
            DeFiNavActions.DEPOSIT_USDC_CIRCLE,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.ADD_LP,
            DeFiNavActions.REMOVE_LP,
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> graph.strategies.default.submit()
        }
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

    private fun loadSelectedCurrency() {
        viewModelScope.launch {
            appCurrency.collect { appCurrency ->
                uiState.update { it.copy(fiatCurrency = appCurrency.ticker) }
            }
        }
    }

    private fun collectAdvanceGasUi() {
        advanceGasUiRepository.showSettings
            .onEach { showGasSettings ->
                uiState.update { it.copy(showGasSettings = showGasSettings) }
            }
            .launchIn(viewModelScope)
    }

    private fun collectSelectedAccount() {
        viewModelScope.launch {
            combine(selectedToken.filterNotNull(), accounts, graph.isSwitchingAccounts) {
                    token,
                    accounts,
                    switching ->
                    if (switching) return@combine null // <-- SKIP during transitions

                    val address = token.address
                    val hasMemo =
                        token.isNativeToken || token.chain.standard == TokenStandard.COSMOS

                    val uiModel =
                        accountToTokenBalanceUiModelMapper(
                            SendSrc(
                                Address(
                                    chain = token.chain,
                                    address = address,
                                    accounts = accounts,
                                ),
                                accounts.find { it.token.id.equals(token.id, true) }
                                    ?: Account(
                                        token = token,
                                        tokenValue = null,
                                        fiatValue = null,
                                        price = null,
                                    ),
                            )
                        )

                    advanceGasUiRepository.updateTokenStandard(token.chain.standard)
                    uiState.update {
                        it.copy(srcAddress = address, selectedCoin = uiModel, hasMemo = hasMemo)
                    }
                }
                .collect()
        }
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
