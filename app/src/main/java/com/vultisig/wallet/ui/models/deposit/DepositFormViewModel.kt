package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.thorchain.RujiStakeBalances
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.load.CacaoMaturityLoader
import com.vultisig.wallet.ui.models.deposit.load.DepositAmountHelper
import com.vultisig.wallet.ui.models.deposit.load.DepositDataLoader
import com.vultisig.wallet.ui.models.deposit.load.DepositFieldInputCoordinator
import com.vultisig.wallet.ui.models.deposit.load.DepositOptionCoordinator
import com.vultisig.wallet.ui.models.deposit.load.LiquidityDataLoader
import com.vultisig.wallet.ui.models.deposit.load.NodeWhitelistChecker
import com.vultisig.wallet.ui.models.deposit.load.RujiBalancesLoader
import com.vultisig.wallet.ui.models.deposit.load.SecuredAssetLoader
import com.vultisig.wallet.ui.models.deposit.submit.DepositStrategyContext
import com.vultisig.wallet.ui.models.deposit.submit.DepositStrategyFactory
import com.vultisig.wallet.ui.models.deposit.submit.DepositSubmitStrategies
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal enum class DepositOption {
    AddCacaoPool,
    Bond,
    Unbond,
    Leave,
    Stake,
    Unstake,
    Custom,
    TransferIbc,
    Switch,
    Merge,
    RemoveCacaoPool,
    UnMerge,
    SecuredAsset,
    WithdrawSecuredAsset,
    AddLiquidity,
    RemoveLiquidity,
}

@Immutable
internal data class DepositFormUiModel(
    val selectedToken: Coin = Coins.ThorChain.RUNE,
    val depositMessage: UiText = UiText.Empty,
    val depositOption: DepositOption = DepositOption.Bond,
    val depositOptions: List<DepositOption> = emptyList(),
    val depositChain: Chain? = null,
    val errorText: UiText? = null,
    val tokenAmountError: UiText? = null,
    val nodeAddressError: UiText? = null,
    val providerError: UiText? = null,
    val operatorFeeError: UiText? = null,
    val customMemoError: UiText? = null,
    val basisPointsError: UiText? = null,
    val assetsError: UiText? = null,
    val lpUnitsError: UiText? = null,
    val slippageError: UiText? = null,
    val isLoading: Boolean = false,
    val isCheckingWhitelist: Boolean = false,
    val isWhitelistFailed: Boolean = false,
    val balance: UiText = UiText.Empty,
    val balanceDecimal: BigDecimal? = null,
    val sharesBalance: UiText = R.string.share_balance_loading.asUiText(),
    val selectedDstChain: Chain = Chain.ThorChain,
    val dstChainList: List<Chain> = emptyList(),
    val dstAddressError: UiText? = null,
    val amountError: UiText? = null,
    val memoError: UiText? = null,
    val thorAddressError: UiText? = null,
    val selectedCoin: TokenMergeInfo = tokensToMerge.first(),
    val selectedUnMergeCoin: TokenMergeInfo = tokensToMerge.first(),
    val coinList: List<TokenMergeInfo> = tokensToMerge,
    val unstakableAmount: String? = null,
    val isUnstakeMature: Boolean = false,
    val unstakeUnlocksInText: UiText? = null,
    val rewardsAmount: String? = null,
    val availableSecuredAssets: List<TokenWithdrawSecureAsset> = emptyList(),
    val securedAssetsLoaded: Boolean = false,
    val selectedSecuredAsset: TokenWithdrawSecureAsset =
        availableSecuredAssets.firstOrNull() ?: TokenWithdrawSecureAsset.EMPTY,
    val bondableAssets: List<String> = emptyList(),
    val selectedBondAsset: String = "",
    val availableLpUnits: String? = null,
    // For Maya: total LP units in the pool. For THORChain remove-LP, this stores the user's own
    // units (the calculator divides by it so that selectedUnits/userUnits gives the redeem
    // fraction).
    val removeLpUnitsDivisor: BigInteger = BigInteger.ZERO,
    // For Maya: pool's CACAO depth (base units). For THORChain remove-LP, this stores the user's
    // pre-computed RUNE redeem value (base units). Renamed from chain-specific names because the
    // semantic differs between flows.
    val removeLpPoolDepth: BigInteger = BigInteger.ZERO,
    val removeLpDecimals: Int = RemoveLpCalculator.CACAO_DECIMALS,
    val removeLpTokenSymbol: String = "CACAO",
    val totalGas: UiText = UiText.Empty,
    val estimatedFee: UiText = UiText.Empty,
    val removeLpPercent: Float = 0f,
    // Slider-derived withdrawal fraction in basis points (0..10000). Stored so the submit path can
    // reuse the exact value used to compute the displayed redeem amount, keeping the shown amount
    // and the on-chain memo in sync at sub-percent granularity.
    val removeLpBasisPoints: Int = 0,
    val removeLpCacaoDisplay: String = "",
)

@HiltViewModel
internal class DepositFormViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val requestQrScan: RequestQrScanUseCase,
    appCurrencyRepository: AppCurrencyRepository,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val requestResultRepository: RequestResultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val balanceRepository: BalanceRepository,
    private val vaultRepository: VaultRepository,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val gasFeeHelper: DepositGasFeeHelper,
    private val liquidityDataLoaderFactory: LiquidityDataLoader.Factory,
    private val securedAssetLoaderFactory: SecuredAssetLoader.Factory,
    private val cacaoMaturityLoaderFactory: CacaoMaturityLoader.Factory,
    private val rujiBalancesLoaderFactory: RujiBalancesLoader.Factory,
    private val nodeWhitelistCheckerFactory: NodeWhitelistChecker.Factory,
    private val dataLoaderFactory: DepositDataLoader.Factory,
    private val depositOptionCoordinatorFactory: DepositOptionCoordinator.Factory,
    private val depositFieldInputCoordinatorFactory: DepositFieldInputCoordinator.Factory,
    private val depositAmountHelperFactory: DepositAmountHelper.Factory,
    private val depositStrategyFactory: DepositStrategyFactory,
) : ViewModel() {

    private val appCurrency =
        appCurrencyRepository.currency.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    val fiatCurrency =
        appCurrency
            .map { it.ticker }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                appCurrencyRepository.defaultCurrency.ticker,
            )

    private var vaultId: String? = null
    private var chain: Chain? = null

    private var rujiStakeBalances = MutableStateFlow<RujiStakeBalances?>(null)

    private val fields = DepositFieldStates()

    val tokenAmountFieldState
        get() = fields.tokenAmountFieldState

    val fiatAmountFieldState
        get() = fields.fiatAmountFieldState

    val nodeAddressFieldState
        get() = fields.nodeAddressFieldState

    val providerFieldState
        get() = fields.providerFieldState

    val operatorFeeFieldState
        get() = fields.operatorFeeFieldState

    val customMemoFieldState
        get() = fields.customMemoFieldState

    val basisPointsFieldState
        get() = fields.basisPointsFieldState

    val lpUnitsFieldState
        get() = fields.lpUnitsFieldState

    val assetsFieldState
        get() = fields.assetsFieldState

    val thorAddressFieldState
        get() = fields.thorAddressFieldState

    val rewardsAmountFieldState
        get() = fields.rewardsAmountFieldState

    val slippageFieldState
        get() = fields.slippageFieldState

    private val _state = MutableStateFlow(DepositFormUiModel())
    val state: StateFlow<DepositFormUiModel> = _state.asStateFlow()
    var isLoading: Boolean
        get() = state.value.isLoading
        set(value) {
            _state.update { it.copy(isLoading = value) }
        }

    private val address = MutableStateFlow<Address?>(null)
    private var depositTypeAction: String? = null
    private var bondAddress: String? = null
    private var lpPoolId: String? = null

    private val liquidityDataLoader: LiquidityDataLoader =
        liquidityDataLoaderFactory.create(
            scope = viewModelScope,
            state = _state,
            address = address,
            assetsFieldState = assetsFieldState,
            lpUnitsFieldState = lpUnitsFieldState,
            vaultId = { vaultId },
            lpPoolId = { lpPoolId },
            resolvePairedAddress = ::resolvePairedAddress,
        )

    private val securedAssetLoader: SecuredAssetLoader =
        securedAssetLoaderFactory.create(
            scope = viewModelScope,
            thorAddressFieldState = thorAddressFieldState,
            vaultId = { vaultId },
            selectedToken = { state.value.selectedToken },
        )

    private val cacaoMaturityLoader: CacaoMaturityLoader =
        cacaoMaturityLoaderFactory.create(
            scope = viewModelScope,
            onResult = { isMature, unlocksInText ->
                _state.update {
                    it.copy(isUnstakeMature = isMature, unstakeUnlocksInText = unlocksInText)
                }
            },
        )

    private val depositOptionCoordinator: DepositOptionCoordinator =
        depositOptionCoordinatorFactory.create(
            scope = viewModelScope,
            state = _state,
            address = address,
            fields = fields,
            liquidityDataLoader = liquidityDataLoader,
            securedAssetLoader = securedAssetLoader,
            cacaoMaturityLoader = cacaoMaturityLoader,
            chainProvider = { chain },
            vaultId = { vaultId },
            bondAddress = { bondAddress },
        )

    private val rujiBalancesLoader: RujiBalancesLoader =
        rujiBalancesLoaderFactory.create(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            addressProvider = { address.value?.address },
            selectedUnMergeCoinProvider = { state.value.selectedUnMergeCoin },
            onSharesBalance = { sharesBalance ->
                _state.update { it.copy(sharesBalance = sharesBalance) }
            },
            setLoading = { isLoading = it },
        )

    private val nodeWhitelistChecker: NodeWhitelistChecker =
        nodeWhitelistCheckerFactory.create(
            scope = viewModelScope,
            state = _state,
            address = address,
            nodeAddressFieldState = nodeAddressFieldState,
            chainProvider = { chain },
        )

    private val fieldInputCoordinator: DepositFieldInputCoordinator =
        depositFieldInputCoordinatorFactory.create(
            scope = viewModelScope,
            state = _state,
            fields = fields,
            nodeWhitelistChecker = nodeWhitelistChecker,
            chainProvider = { chain },
            vaultId = { vaultId },
        )

    private val dataLoader: DepositDataLoader =
        dataLoaderFactory.create(
            scope = viewModelScope,
            address = address,
            depositTypeActionProvider = { depositTypeAction },
            clearDepositTypeAction = { depositTypeAction = null },
            selectDepositOption = ::selectDepositOption,
        )

    private val depositAmountHelper: DepositAmountHelper =
        depositAmountHelperFactory.create(
            scope = viewModelScope,
            fields = fields,
            appCurrency = appCurrency,
            state = _state,
            chain = { chain },
            vaultId = { vaultId },
        )

    private val depositStrategies: DepositSubmitStrategies =
        depositStrategyFactory.create(
            DepositStrategyContext(
                vaultId = { vaultId },
                chain = { chain },
                state = { state.value },
                address = { address.value },
                lpPoolId = { lpPoolId },
                fields = fields,
                blockChainSpecificRepository = blockChainSpecificRepository,
                calculateGasFee = depositAmountHelper::calculateGasFee,
                getFeesFiatValue = depositAmountHelper::getFeesFiatValue,
                selectedToken = ::getSelectedToken,
                selectedAccount = ::getSelectedAccount,
                requireTokenAmount = depositAmountHelper::requireTokenAmount,
                resolvePairedAddress = ::resolvePairedAddress,
                resolveSecuredAssetInboundAddress =
                    depositOptionCoordinator::requireSecuredAssetInboundAddress,
                getBitcoinTransactionPlan = depositAmountHelper::getBitcoinTransactionPlan,
                rujiMergeBalances = { rujiBalancesLoader.balances },
            )
        )

    /**
     * Screen-entry hook: records the navigation arguments, starts amount-field collection, then
     * delegates all init-time flow wiring to [DepositDataLoader.wireInitialState].
     *
     * @param vaultId active vault id.
     * @param chainId raw chain identifier, parsed into a [Chain].
     * @param depositType optional deep-link deposit action (e.g. ADD_LP) consumed during wiring.
     * @param bondAddress optional pre-filled bond address.
     * @param poolId optional liquidity pool id.
     */
    fun loadData(
        vaultId: String,
        chainId: String,
        depositType: String?,
        bondAddress: String?,
        poolId: String? = null,
    ) {
        this.vaultId = vaultId
        val chain = chainId.let(Chain::fromRaw)
        this.chain = chain
        this.depositTypeAction = depositType
        this.bondAddress = bondAddress
        this.lpPoolId = poolId

        depositAmountHelper.collectAmountChanges()

        dataLoader.wireInitialState(
            vaultId = vaultId,
            chain = chain,
            tokensToMerge = tokensToMerge,
            state = _state,
            updateTokenAmount = depositAmountHelper::updateTokenAmount,
            selectDstChain = ::selectDstChain,
            collectSecuredAssetAddresses = securedAssetLoader::collectSecuredAssetAddresses,
            loadGasFeeForDisplay = { vaultId, chain, address ->
                gasFeeHelper.loadGasFeeForDisplay(
                    scope = viewModelScope,
                    vaultId = vaultId,
                    chain = chain,
                    address = address,
                    onResult = { totalGas, estimatedFee ->
                        _state.update { it.copy(totalGas = totalGas, estimatedFee = estimatedFee) }
                    },
                )
            },
        )
    }

    fun selectBondAsset(asset: String) {
        val pool = liquidityDataLoader.bondPoolFor(asset)
        _state.update {
            it.copy(
                selectedBondAsset = asset,
                availableLpUnits = pool?.availableUnits,
                removeLpUnitsDivisor = pool?.totalPoolLpUnits?.toBigInteger() ?: BigInteger.ZERO,
                removeLpPoolDepth = pool?.poolCacaoDepth?.toBigInteger() ?: BigInteger.ZERO,
                lpUnitsError = null,
            )
        }
        lpUnitsFieldState.clearText()
        assetsFieldState.setTextAndPlaceCursorAtEnd(asset)
    }

    fun setMaxLpUnits() {
        liquidityDataLoader.setMaxLpUnits()
    }

    fun setRemoveLpPercent(percent: Float) {
        liquidityDataLoader.setRemoveLpPercent(percent)
    }

    fun selectToken() {
        val chain = chain ?: return

        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val requestId = UUID.randomUUID().toString()

            navigator.route(
                Route.SelectAsset(
                    requestId = requestId,
                    vaultId = vaultId,
                    preselectedNetworkId = chain.id,
                    networkFilters = Route.SelectNetwork.Filters.DisableNetworkSelection,
                )
            )
            val selectedAsset = requestResultRepository.request<AssetSelected?>(requestId)
            val selectedToken = selectedAsset?.token

            if (selectedToken != null) {
                _state.update { it.copy(selectedToken = selectedToken) }
            }
        }
    }

    fun selectDepositOption(option: DepositOption) {
        depositOptionCoordinator.selectDepositOption(option)
    }

    /**
     * Selects [chain] as the deposit destination; see
     * [DepositFieldInputCoordinator.selectDstChain].
     */
    fun selectDstChain(chain: Chain) = fieldInputCoordinator.selectDstChain(chain)

    fun selectMergeToken(mergeInfo: TokenMergeInfo) {
        _state.update { it.copy(selectedCoin = mergeInfo) }
    }

    fun selectUnMergeToken(unmergeInfo: TokenMergeInfo) {
        _state.update { it.copy(selectedUnMergeCoin = unmergeInfo) }
        if (rujiBalancesLoader.balances == null) {
            onLoadRujiMergeBalances()
        } else {
            rujiBalancesLoader.setUnMergeTokenSharesField(unmergeInfo)
        }
    }

    /**
     * Validates the destination-address field; see
     * [DepositFieldInputCoordinator.validateDstAddress].
     */
    fun validateDstAddress() = fieldInputCoordinator.validateDstAddress()

    /** Validates the node-address field; see [DepositFieldInputCoordinator.validateNodeAddress]. */
    fun validateNodeAddress() = fieldInputCoordinator.validateNodeAddress()

    /** Validates the token-amount field; see [DepositFieldInputCoordinator.validateTokenAmount]. */
    fun validateTokenAmount() = fieldInputCoordinator.validateTokenAmount()

    /** Validates the token amount and triggers [deposit] only when the field has no error. */
    fun validateAndDeposit() {
        fieldInputCoordinator.validateTokenAmount()
        if (state.value.tokenAmountError == null) {
            deposit()
        }
    }

    /**
     * Validates the provider-address field; see [DepositFieldInputCoordinator.validateProvider].
     */
    fun validateProvider() = fieldInputCoordinator.validateProvider()

    /** Validates the operator-fee field; see [DepositFieldInputCoordinator.validateOperatorFee]. */
    fun validateOperatorFee() = fieldInputCoordinator.validateOperatorFee()

    /** Validates the custom-memo field; see [DepositFieldInputCoordinator.validateCustomMemo]. */
    fun validateCustomMemo() = fieldInputCoordinator.validateCustomMemo()

    /** Validates the basis-points field; see [DepositFieldInputCoordinator.validateBasisPoints]. */
    fun validateBasisPoints() = fieldInputCoordinator.validateBasisPoints()

    /** Validates the slippage field; see [DepositFieldInputCoordinator.validateSlippage]. */
    fun validateSlippage() = fieldInputCoordinator.validateSlippage()

    /** Sets the provider-address field; see [DepositFieldInputCoordinator.setProvider]. */
    fun setProvider(provider: String) = fieldInputCoordinator.setProvider(provider)

    /**
     * Sets the node-address field and revalidates; see
     * [DepositFieldInputCoordinator.setNodeAddress].
     */
    fun setNodeAddress(address: String) = fieldInputCoordinator.setNodeAddress(address)

    /**
     * Sets the destination-address field and revalidates; see
     * [DepositFieldInputCoordinator.setDstAddress].
     */
    fun setDstAddress(address: String) = fieldInputCoordinator.setDstAddress(address)

    /**
     * Validates the THORChain destination address; see
     * [DepositFieldInputCoordinator.validateThorAddress].
     */
    fun validateThorAddress() = fieldInputCoordinator.validateThorAddress()

    /**
     * Sets the THORChain destination address and revalidates; see
     * [DepositFieldInputCoordinator.setThorAddress].
     */
    fun setThorAddress(address: String) = fieldInputCoordinator.setThorAddress(address)

    fun scan() {
        viewModelScope.launch {
            val qr = requestQrScan()
            if (!qr.isNullOrBlank()) {
                fieldInputCoordinator.setNodeAddress(qr)
            }
        }
    }

    fun openAddressBook() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val chainId = chain?.id ?: return@launch
            val address: AddressBookEntry =
                requestAddressBookEntry(chainId = chainId, excludeVaultId = vaultId)
                    ?: return@launch
            fieldInputCoordinator.setNodeAddress(address.address)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorText = null) }
    }

    fun deposit() {
        viewModelScope.launch {
            try {
                val vaultId = vaultId ?: return@launch
                isLoading = true

                val transaction = depositStrategies.getValue(state.value.depositOption).build()

                transactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(transactionId = transaction.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
                showError(UiText.StringResource(R.string.dialog_default_error_body))
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * For symmetric LP add the memo carries the user's address on the *paired* chain so THORChain
     * can credit them when the asset half is later deposited from that chain. Returns null when the
     * pool refers to the native chain (no pair) or when the asset chain can't be resolved.
     */
    private suspend fun resolvePairedAddress(
        chain: Chain,
        vaultId: String,
        poolId: String,
    ): String? {
        if (chain != Chain.ThorChain) return null
        val parsed =
            parseThorChainPool(poolId).takeIf { it.chain != null && it.chain != Chain.ThorChain }
                ?: return null
        val assetChain = parsed.chain ?: return null
        return try {
            val vault = vaultRepository.get(vaultId) ?: return null
            chainAccountAddressRepository.getAddress(chain = assetChain, vault = vault).first
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to resolve paired address for $poolId")
            null
        }
    }

    private fun getSelectedToken(): Coin? {
        return getSelectedAccount()?.token
    }

    private fun getSelectedAccount(): Account? {
        val address = address.value ?: return null
        val userSelectedToken = state.value.selectedToken
        return address.accounts.firstOrNull { it.token.id == userSelectedToken.id }
    }

    fun onLoadRujiMergeBalances() {
        rujiBalancesLoader.loadRujiMergeBalances()
    }

    private fun showError(text: UiText) {
        _state.update { it.copy(errorText = text) }
    }

    /** Validates the assets field; see [DepositFieldInputCoordinator.validateAssets]. */
    fun validateAssets() = fieldInputCoordinator.validateAssets()

    /** Validates the LP-units field; see [DepositFieldInputCoordinator.validateLpUnits]. */
    fun validateLpUnits() = fieldInputCoordinator.validateLpUnits()

    fun onSelectSecureAsset(asset: TokenWithdrawSecureAsset) {
        val balance = asset.tokenValue?.let(mapTokenValueToStringWithUnit)
        _state.update {
            it.copy(selectedSecuredAsset = asset, balance = balance?.asUiText() ?: UiText.Empty)
        }
    }
}
