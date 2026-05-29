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
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
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
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.AccountValidator
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.models.send.submit.SendStrategyContext
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
import wallet.core.jni.proto.Bitcoin

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

    private val planFee = MutableStateFlow<Long?>(null)
    private val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    private var gasSettings = MutableStateFlow<GasSettings?>(null)

    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)

    private val isSwitchingAccounts = MutableStateFlow(false)

    private val tronFrozenBalances =
        MutableStateFlow<TronFrozenBalanceState>(TronFrozenBalanceState.Loading)

    private val tokenPreselectionService =
        TokenPreselectionService(
            scope = viewModelScope,
            accountsState = accountsState,
            defiTypeProvider = { defiType },
            selectedTokenProvider = { selectedTokenValue },
            onTokenSelected = { tokenNetworkSelectionDelegate.selectToken(it) },
        )

    private val accountsLoader =
        AccountsLoader(
            scope = viewModelScope,
            accountsState = accountsState,
            accountsRepository = accountsRepository,
            stakingDetailsRepository = stakingDetailsRepository,
            defiTypeProvider = { defiType },
            mscaAddressProvider = { mscaAddress },
        )

    private val tronStakingService =
        TronStakingService(
            scope = viewModelScope,
            uiState = uiState,
            tronFrozenBalances = tronFrozenBalances,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            defiTypeProvider = { defiType },
            vaultProvider = { vault },
            vaultIdProvider = { vaultId },
            vaultRepository = vaultRepository,
            getTronFrozenBalances = getTronFrozenBalances,
        )

    private val addressManager =
        AddressManager(
            scope = viewModelScope,
            addressFieldState = addressFieldState,
            selectedToken = selectedToken,
            chainAccountAddressRepository = chainAccountAddressRepository,
            addressParserRepository = addressParserRepository,
        )

    private val amountManager =
        AmountManager(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            selectedToken = selectedToken,
            gasFee = gasFee,
            accountProvider = { selectedAccount },
            appCurrency = appCurrency,
            chainValidationService = chainValidationService,
            tokenPriceRepository = tokenPriceRepository,
        )

    private val accountValidator =
        AccountValidator(
            vaultIdProvider = { vaultId },
            selectedAccountProvider = { selectedAccount },
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            gasFee = gasFee,
            addressParserRepository = addressParserRepository,
        )

    private val bitcoinPlanService = BitcoinPlanService(vaultRepository)

    private val gasFeeOrchestrator =
        GasFeeOrchestrator(
            scope = viewModelScope,
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
            vaultProvider = { vault },
            vaultIdProvider = { vaultId },
            accountProvider = { selectedAccount },
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

    private val amountFractionManager =
        AmountFractionManager(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            memoFieldState = memoFieldState,
            uiState = uiState,
            gasFee = gasFee,
            gasSettings = gasSettings,
            specific = specific,
            defiTypeProvider = { defiType },
            vaultProvider = { vault },
            accountProvider = { selectedAccount },
            currentTronFrozenBalanceProvider = tronStakingService::currentFrozenBalance,
            getAvailableTokenBalance = getAvailableTokenBalance,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            adjustGasFee = ::adjustGasFee,
            amountManager = amountManager,
        )

    private val tokenNetworkSelectionDelegate =
        TokenNetworkSelectionDelegate(
            scope = viewModelScope,
            navigator = navigator,
            requestResultRepository = requestResultRepository,
            tokenRepository = tokenRepository,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenPreselectionService = tokenPreselectionService,
            accountsLoader = accountsLoader,
            amountFractionManager = amountFractionManager,
            amountManager = amountManager,
            vaultIdProvider = { vaultId },
            accounts = accounts,
            selectedToken = selectedToken,
            expandSection = ::expandSection,
        )

    private val strategies =
        sendStrategyFactory.create(
            SendStrategyContext(
                scope = viewModelScope,
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
                vaultIdProvider = { vaultId },
                selectedAccountProvider = { selectedAccount },
                defiTypeProvider = { defiType },
                isAutocompoundProvider = { uiState.value.isAutocompound },
                mscaAddressProvider = { mscaAddress },
                currentTronFrozenBalanceProvider = tronStakingService::currentFrozenBalance,
                expandSection = ::expandSection,
                emitFocusField = { field -> _focusFieldChannel.trySend(field) },
                showLoading = ::showLoading,
                hideLoading = ::hideLoading,
                showError = ::showError,
            )
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
        gasFeeOrchestrator.start()
        collectAdvanceGasUi()
        loadVaultName()
        loadGasSettings()
        addressManager.start()
        amountManager.start()
        observeManagers()
    }

    private fun observeManagers() {
        viewModelScope.launch {
            addressManager.isDstAddressComplete.collect { isComplete ->
                uiState.update { it.copy(isDstAddressComplete = isComplete) }
            }
        }
        viewModelScope.launch {
            addressManager.onAddressValidated.collect { expandSection(SendSections.Amount) }
        }
        viewModelScope.launch {
            amountManager.reapingError.collect { error ->
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
        memoFieldState.clearText()
        this.defiType =
            if (type == null) {
                null
            } else {
                parseDepositType(type)
            }

        val mscaChanged = this.mscaAddress != mscaAddress
        if (mscaChanged) {
            this.mscaAddress = mscaAddress
        }

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId
            accountsLoader.load(vaultId)
            loadVaultName()
            initFormType()
        } else if (mscaChanged && defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE) {
            // The Circle USDC account is built from mscaAddress; without this reload the form
            // would stay on the zero-balance placeholder until the screen is recreated.
            accountsLoader.load(vaultId)
        }

        if (address != null) {
            setAddressFromQrCode(
                qrCode = address,
                preSelectedChainId = preSelectedChainId,
                preSelectedTokenId = preSelectedTokenId,
            )
        } else {
            tokenPreselectionService.preSelect(
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
            if (tronStakingService.isStakingType()) TronResourceType.BANDWIDTH else null
        uiState.update {
            it.copy(
                defiType = this.defiType,
                isAutocompound = autoCompound,
                tronResourceType = initialResourceType,
            )
        }
        tronStakingService.initIfStakingType()
    }

    fun setTronResourceType(type: TronResourceType) {
        // Preempt any in-flight percentage calc so it can't resume after the field clears
        // and overwrite them with the amount computed for the previous resource type.
        amountFractionManager.cancel()
        tronStakingService.setResourceType(type)
    }

    private fun loadVaultName() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            vaultRepository.get(vaultId)?.let { vault ->
                this@SendFormViewModel.vault = vault
                uiState.update { it.copy(srcVaultName = vault.name) }
            }
        }
    }

    fun validateTokenAmount() {
        val errorText = amountManager.validateTokenAmount(tokenAmountFieldState.text.toString())
        uiState.update { it.copy(tokenAmountError = errorText) }
    }

    fun selectNetwork() = tokenNetworkSelectionDelegate.selectNetwork()

    fun onNetworkLongPressStarted(position: Offset) =
        tokenNetworkSelectionDelegate.onNetworkLongPressStarted(position)

    fun openTokenSelection() = tokenNetworkSelectionDelegate.openTokenSelection()

    fun openTokenSelectionPopup(position: Offset) =
        tokenNetworkSelectionDelegate.openTokenSelectionPopup(position)

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

                    tokenNetworkSelectionDelegate.checkChainIdExistInAccounts(
                        preSelectedChainIds = preSelectedChainIds,
                        vaultId = vaultId,
                    )

                    tokenPreselectionService.preSelect(
                        preSelectedChainIds = preSelectedChainIds,
                        preSelectedTokenId = preSelectedTokenId,
                        forcePreselection = true,
                    )
                }
            }
        }
    }

    fun setOutputAddress(address: String) {
        addressManager.setOutputAddress(address)
    }

    fun setProviderAddress(address: String) {
        providerBondFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scanAddress() {
        viewModelScope.launch {
            val qr = requestQrScan.invoke()
            if (!qr.isNullOrBlank()) {
                setAddressFromQrCode(qr, null, null)
            }
        }
    }

    fun scanProviderAddress() {
        viewModelScope.launch {
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

        isSwitchingAccounts.value = true
        selectedToken.value = null

        // Route the data-source switch through AccountsLoader so accountsState has a single
        // writer — the previous in-VM collect raced against the already-running loader,
        // and for UNSTAKE_TCY the two collectors pulled from different APIs. The callback
        // runs per emission so we can pin the token selection on the first emission
        // containing the target ticker (the tokenSelected flag prevents a later hydration
        // emission from re-calling selectToken, which would wipe the user's typed amount).
        val targetTicker = if (checked) "sTCY" else "TCY"
        var tokenSelected = false
        accountsLoader.loadForAutoCompoundSwitch(vaultId = vaultId, useStableCompound = checked) {
            loadedAccounts ->
            // Release the gate on every emission — if the target ticker is never found the
            // form must still become interactive rather than staying gated forever. Setting
            // to false repeatedly is a no-op once already false.
            isSwitchingAccounts.value = false
            if (!tokenSelected) {
                loadedAccounts
                    .find {
                        it.token.ticker.equals(targetTicker, true) &&
                            it.token.chain == Chain.ThorChain
                    }
                    ?.let {
                        tokenSelected = true
                        tokenNetworkSelectionDelegate.selectToken(it.token)
                    }
            }
        }
    }

    fun openAddressBook(addressType: AddressBookType = AddressBookType.OUTPUT) {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            val address: AddressBookEntry =
                requestAddressBookEntry(chainId = selectedChain.id, excludeVaultId = vaultId)
                    ?: return@launch

            when (addressType) {
                AddressBookType.OUTPUT -> {
                    val selectedNewChain = address.chain
                    tokenNetworkSelectionDelegate.checkIfTokenSelectionRequired(
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
        gasSettings.value = settings
        if (settings is GasSettings.UTXO) {
            val currentSpec = specific.value ?: return
            val utxoSpec = currentSpec.blockChainSpecific as? BlockChainSpecific.UTXO ?: return
            specific.value =
                currentSpec.copy(blockChainSpecific = utxoSpec.copy(byteFee = settings.byteFee))
        }
    }

    fun chooseMaxTokenAmount() = amountFractionManager.chooseMaxTokenAmount()

    fun choosePercentageAmount(amountFraction: AmountFraction) =
        amountFractionManager.choosePercentageAmount(amountFraction)

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun onClickContinue() {
        when (uiState.value.defiType) {
            DeFiNavActions.BOND -> strategies.bond.submit()
            DeFiNavActions.UNBOND -> strategies.unbond.submit()
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY -> strategies.stake.submit()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.WITHDRAW_RUJI -> strategies.unstake.submit()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> strategies.mint.submit()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> strategies.redeem.submit()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> strategies.withdrawUsdcCircle.submit()

            null,
            DeFiNavActions.DEPOSIT_USDC_CIRCLE,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.ADD_LP,
            DeFiNavActions.REMOVE_LP,
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> strategies.default.submit()
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
            combine(selectedToken.filterNotNull(), accounts, isSwitchingAccounts) {
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

    fun refreshGasFee() = gasFeeOrchestrator.refresh()
}
