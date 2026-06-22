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
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.load.CacaoMaturityLoader
import com.vultisig.wallet.ui.models.deposit.load.DepositAmountHelper
import com.vultisig.wallet.ui.models.deposit.load.DepositDataLoader
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
import kotlinx.coroutines.flow.firstOrNull
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
    private val accountsRepository: AccountsRepository,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val requestResultRepository: RequestResultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val balanceRepository: BalanceRepository,
    private val vaultRepository: VaultRepository,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val fieldValidator: DepositFieldValidator,
    private val gasFeeHelper: DepositGasFeeHelper,
    private val liquidityDataLoaderFactory: LiquidityDataLoader.Factory,
    private val securedAssetLoaderFactory: SecuredAssetLoader.Factory,
    private val cacaoMaturityLoaderFactory: CacaoMaturityLoader.Factory,
    private val rujiBalancesLoaderFactory: RujiBalancesLoader.Factory,
    private val nodeWhitelistCheckerFactory: NodeWhitelistChecker.Factory,
    private val dataLoaderFactory: DepositDataLoader.Factory,
    private val depositOptionCoordinatorFactory: DepositOptionCoordinator.Factory,
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

    fun selectDstChain(chain: Chain) {
        nodeAddressFieldState.clearText()

        _state.update { it.copy(selectedDstChain = chain, dstAddressError = null) }

        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val address = accountsRepository.loadAddress(vaultId, chain).firstOrNull()

            if (address != null) {
                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
            }
        }
    }

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
     * Validates the destination address shown on the IBC Transfer and Switch sub-forms against the
     * appropriate chain (selected destination chain for IBC, source/Gaia chain for Switch),
     * surfacing inline errors via [DepositFormUiModel.dstAddressError]. Other deposit options leave
     * the field error untouched.
     */
    fun validateDstAddress() {
        val depositOption = state.value.depositOption
        val validationChain =
            when (depositOption) {
                DepositOption.TransferIbc -> state.value.selectedDstChain
                DepositOption.Switch -> chain
                else -> return
            }
        val dstAddress = nodeAddressFieldState.text.toString()
        // For Switch the dst field is auto-populated from the THORChain inbound vault. When the
        // fetch returns halt/unavailable, the field is left blank and dstAddressError carries the
        // actionable reason; running the generic blank-check here would clobber that context.
        // Only skip when dstAddressError is already set (halt/unavailable) — if it's null the user
        // manually cleared the field in the healthy path, so we must validate and surface the blank
        // error to block Continue.
        if (
            depositOption == DepositOption.Switch &&
                dstAddress.isBlank() &&
                state.value.dstAddressError != null
        )
            return
        val error = fieldValidator.dstAddressErrorOrNull(validationChain, dstAddress)
        _state.update { it.copy(dstAddressError = error) }
    }

    fun validateNodeAddress() {
        val nodeAddress = nodeAddressFieldState.text.toString()
        val errorText = fieldValidator.addressErrorOrNull(chain, nodeAddress)
        if (errorText != null) {
            nodeWhitelistChecker.cancel()
            _state.update { it.copy(nodeAddressError = errorText, isCheckingWhitelist = false) }
            return
        }
        if (chain == Chain.MayaChain && state.value.depositOption == DepositOption.Bond) {
            nodeWhitelistChecker.check(nodeAddress)
        } else {
            _state.update { it.copy(nodeAddressError = null) }
        }
    }

    fun validateTokenAmount() {
        val errorText = fieldValidator.validateTokenAmount(tokenAmountFieldState.text.toString())
        _state.update { it.copy(tokenAmountError = errorText) }
    }

    fun validateAndDeposit() {
        validateTokenAmount()
        if (state.value.tokenAmountError == null) {
            deposit()
        }
    }

    fun validateProvider() {
        val errorText = fieldValidator.addressErrorOrNull(chain, providerFieldState.text.toString())
        _state.update { it.copy(providerError = errorText) }
    }

    fun validateOperatorFee() {
        val text = operatorFeeFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = fieldValidator.validateBasisPoints(text.toIntOrNull())
            _state.update { it.copy(operatorFeeError = errorText) }
        }
    }

    fun validateCustomMemo() {
        val errorText = fieldValidator.validateCustomMemo(customMemoFieldState.text.toString())
        _state.update { it.copy(customMemoError = errorText) }
    }

    fun validateBasisPoints() {
        val text = basisPointsFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = fieldValidator.validateBasisPoints(text.toIntOrNull())
            _state.update { it.copy(basisPointsError = errorText) }
        }
    }

    fun validateSlippage() {
        val text = slippageFieldState.text.toString()
        val errorText = fieldValidator.validateSlippage(text)
        _state.update { it.copy(slippageError = errorText) }
    }

    fun setProvider(provider: String) {
        providerFieldState.setTextAndPlaceCursorAtEnd(provider)
    }

    fun setNodeAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateNodeAddress()
    }

    /** Sets the destination address on the IBC Transfer / Switch sub-forms and revalidates. */
    fun setDstAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateDstAddress()
    }

    /**
     * Validates the destination THORChain address on the Switch sub-form against ThorChain,
     * surfacing inline errors via [DepositFormUiModel.thorAddressError]. No-op outside the Switch
     * flow so SECURE+ auto-populated values do not trigger inline errors.
     */
    fun validateThorAddress() {
        if (state.value.depositOption != DepositOption.Switch) return
        val errorText =
            fieldValidator.addressErrorOrNull(
                Chain.ThorChain,
                thorAddressFieldState.text.toString(),
            )
        _state.update { it.copy(thorAddressError = errorText) }
    }

    /** Sets the THORChain destination address on the Switch sub-form and revalidates. */
    fun setThorAddress(address: String) {
        thorAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateThorAddress()
    }

    private fun setSlippage(slippage: String) {
        slippageFieldState.setTextAndPlaceCursorAtEnd(slippage)
    }

    fun scan() {
        viewModelScope.launch {
            val qr = requestQrScan()
            if (!qr.isNullOrBlank()) {
                setNodeAddress(qr)
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
            setNodeAddress(address.address)
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

    fun validateAssets() {
        val assets = assetsFieldState.text.toString()
        _state.update {
            it.copy(
                assetsError =
                    if (!isAssetCharsValid(assets))
                        UiText.StringResource(R.string.deposit_error_invalid_assets)
                    else null
            )
        }
    }

    fun validateLpUnits() {
        val lpUnits = lpUnitsFieldState.text.toString()
        _state.update {
            it.copy(
                lpUnitsError =
                    if (!fieldValidator.isLpUnitCharsValid(lpUnits))
                        UiText.StringResource(R.string.deposit_error_invalid_lpunits)
                    else null
            )
        }
    }

    fun onSelectSecureAsset(asset: TokenWithdrawSecureAsset) {
        val balance = asset.tokenValue?.let(mapTokenValueToStringWithUnit)
        _state.update {
            it.copy(selectedSecuredAsset = asset, balance = balance?.asUiText() ?: UiText.Empty)
        }
    }
}

internal data class TokenMergeInfo(val ticker: String, val contract: String) {

    val denom: String
        get() = "thor.$ticker".lowercase()
}

internal data class TokenWithdrawSecureAsset(
    val ticker: String,
    val contract: String,
    val coin: Coin,
    val tokenValue: TokenValue?,
) {
    companion object {
        val EMPTY =
            TokenWithdrawSecureAsset(
                ticker = "Select Asset",
                contract = "",
                coin = Coin.EMPTY,
                tokenValue = null,
            )
    }
}

private val tokensToMerge =
    listOf(
        TokenMergeInfo(
            ticker = "KUJI",
            contract = "thor14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s3p2nzy",
        ),
        TokenMergeInfo(
            ticker = "rKUJI",
            contract = "thor1yyca08xqdgvjz0psg56z67ejh9xms6l436u8y58m82npdqqhmmtqrsjrgh",
        ),
        TokenMergeInfo(
            ticker = "FUZN",
            contract = "thor1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsw5xx2d",
        ),
        TokenMergeInfo(
            ticker = "NSTK",
            contract = "thor1cnuw3f076wgdyahssdkd0g3nr96ckq8cwa2mh029fn5mgf2fmcmsmam5ck",
        ),
        TokenMergeInfo(
            ticker = "WINK",
            contract = "thor1yw4xvtc43me9scqfr2jr2gzvcxd3a9y4eq7gaukreugw2yd2f8tsz3392y",
        ),
        TokenMergeInfo(
            ticker = "LVN",
            contract = "thor1ltd0maxmte3xf4zshta9j5djrq9cl692ctsp9u5q0p9wss0f5lms7us4yf",
        ),
    )
