@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
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
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.AccountValidator
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.models.send.submit.SendStrategies
import com.vultisig.wallet.ui.models.send.submit.SendStrategyContext
import com.vultisig.wallet.ui.models.send.submit.SendStrategyFactory
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
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
    private val selectedTokenProvider: () -> Coin?,
    private val selectedAccountProvider: () -> Account?,
    private val isAutocompoundProvider: () -> Boolean,
    private val expandSection: (SendSections) -> Unit,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (UiText) -> Unit,
    private val emitFocusField: (SendFocusField) -> Unit,
) {
    private var vault: Vault? = null
    private var vaultId: VaultId? = null
    private var defiType: DeFiNavActions? = null // Default is send, no defi form
    private var mscaAddress: String? = null

    private val vaultProvider: () -> Vault? = { vault }
    private val vaultIdProvider: () -> VaultId? = { vaultId }
    private val defiTypeProvider: () -> DeFiNavActions? = { defiType }
    private val mscaAddressProvider: () -> String? = { mscaAddress }

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

    /**
     * Loads the initial form state from the navigation [args]: resolves the DeFi type, loads
     * accounts, preselects the deep-linked token/address, and seeds amount/memo/slippage.
     *
     * @param args the `Route.Send` navigation arguments that opened the form.
     */
    fun initialize(args: Route.Send) {
        defiType = if (args.type == null) null else parseDepositType(args.type)
        mscaAddress = args.mscaAddress
        vaultId = args.vaultId
        accountsLoader.load(args.vaultId)
        loadVaultName()
        initFormType()

        if (args.address != null) {
            setAddressFromQrCode(
                qrCode = args.address,
                preSelectedChainId = args.chainId,
                preSelectedTokenId = args.tokenId,
            )
        } else {
            tokenPreselectionService.preSelect(
                preSelectedChainIds = listOf(args.chainId),
                preSelectedTokenId = args.tokenId,
            )
        }

        if (args.tokenId != null && args.address == null) {
            expandSection(SendSections.Address)
        }

        if (args.tokenId != null && args.address != null) {
            expandSection(SendSections.Amount)
        }

        args.amount?.let { tokenAmountFieldState.setTextAndPlaceCursorAtEnd(it) }

        args.memo?.let { memoFieldState.setTextAndPlaceCursorAtEnd(it) }

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
                defiType = defiType,
                isAutocompound = autoCompound,
                tronResourceType = initialResourceType,
            )
        }
        tronStakingService.initIfStakingType()
    }

    private fun loadVaultName() {
        scope.safeLaunch {
            val id = vaultId ?: return@safeLaunch
            val loaded = vaultRepository.get(id) ?: return@safeLaunch
            vault = loaded
            uiState.update { it.copy(srcVaultName = loaded.name) }
        }
    }

    /** Launches the collectors that mirror collaborator/repository state into [uiState]. */
    fun bindUiState() {
        scope.launch {
            addressManager.isDstAddressComplete.collect { isComplete ->
                uiState.update { it.copy(isDstAddressComplete = isComplete) }
            }
        }
        scope.launch {
            addressManager.onAddressValidated.collect { expandSection(SendSections.Amount) }
        }
        scope.launch {
            amountManager.reapingError.collect { error ->
                uiState.update { it.copy(reapingError = error) }
            }
        }
        scope.launch {
            advanceGasUiRepository.shouldShowAdvanceGasSettingsIcon.collect {
                shouldShowAdvanceGasSettingsIcon ->
                uiState.update { it.copy(hasGasSettings = shouldShowAdvanceGasSettingsIcon) }
            }
        }
        scope.launch {
            appCurrency.collect { currency ->
                uiState.update { it.copy(fiatCurrency = currency.ticker) }
            }
        }
        advanceGasUiRepository.showSettings
            .onEach { showGasSettings ->
                uiState.update { it.copy(showGasSettings = showGasSettings) }
            }
            .launchIn(scope)
        scope.launch { collectSelectedAccount() }
    }

    private suspend fun collectSelectedAccount() {
        combine(selectedToken.filterNotNull(), accounts, isSwitchingAccounts) {
                token,
                accounts,
                switching ->
                if (switching) return@combine null // <-- SKIP during transitions

                val address = token.address
                val hasMemo = token.isNativeToken || token.chain.standard == TokenStandard.COSMOS

                val uiModel =
                    accountToTokenBalanceUiModelMapper(
                        SendSrc(
                            Address(chain = token.chain, address = address, accounts = accounts),
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

    /**
     * Applies a scanned/deep-linked address to [fieldState], switching the selected chain/token
     * when the address belongs to a different chain than the current selection.
     *
     * @param qrCode the scanned address payload (ignored when null/blank).
     * @param preSelectedChainId optional chain to constrain the address to.
     * @param preSelectedTokenId optional token to preselect after a chain switch.
     * @param fieldState the address field to populate (destination address by default).
     */
    fun setAddressFromQrCode(
        qrCode: String?,
        preSelectedChainId: ChainId?,
        preSelectedTokenId: TokenId?,
        fieldState: TextFieldState = addressFieldState,
    ) {
        if (qrCode.isNullOrBlank()) return
        Timber.d("setAddressFromQrCode(address = $qrCode)")

        fieldState.setTextAndPlaceCursorAtEnd(qrCode)

        val vaultId = vaultId
        if (vaultId.isNullOrBlank()) return

        val chainValidForAddress =
            preSelectedChainId?.let { listOf(Chain.fromRaw(preSelectedChainId)) }
                ?: Chain.entries.filter { chain ->
                    chainAccountAddressRepository.isValid(chain, qrCode)
                }

        val selectedChain = selectedTokenProvider()?.chain

        if (chainValidForAddress.isNotEmpty() && !chainValidForAddress.contains(selectedChain)) {
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

    /**
     * Toggles the staking auto-compound mode, switching the underlying account data source (TCY ⇄
     * sTCY) and pinning the matching token once it loads.
     *
     * @param checked whether auto-compound (stable compound) is enabled.
     */
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

    /**
     * Opens the address book and applies the chosen entry to the output or provider field.
     *
     * @param addressType which field the selected address should populate.
     */
    fun openAddressBook(addressType: AddressBookType = AddressBookType.OUTPUT) {
        scope.safeLaunch {
            val vaultId = vaultId ?: return@safeLaunch
            val selectedChain = selectedTokenProvider()?.chain ?: return@safeLaunch

            val address =
                requestAddressBookEntry(chainId = selectedChain.id, excludeVaultId = vaultId)
                    ?: return@safeLaunch

            when (addressType) {
                AddressBookType.OUTPUT -> {
                    tokenNetworkSelectionDelegate.checkIfTokenSelectionRequired(
                        currentChain = selectedChain,
                        newChain = address.chain,
                    )
                    addressManager.setOutputAddress(address.address)
                }

                AddressBookType.PROVIDER -> {
                    providerBondFieldState.setTextAndPlaceCursorAtEnd(address.address)
                }
            }
        }
    }
}
