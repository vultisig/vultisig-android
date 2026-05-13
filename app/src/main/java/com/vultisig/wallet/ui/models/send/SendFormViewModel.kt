@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R.string
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
import com.vultisig.wallet.data.models.ImageModel
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
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.AmountFraction.F100
import com.vultisig.wallet.ui.models.send.AmountFraction.F25
import com.vultisig.wallet.ui.models.send.AmountFraction.F50
import com.vultisig.wallet.ui.models.send.AmountFraction.F75
import com.vultisig.wallet.ui.models.send.submit.AccountValidator
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.models.send.submit.BondStrategy
import com.vultisig.wallet.ui.models.send.submit.DefaultSendStrategy
import com.vultisig.wallet.ui.models.send.submit.MintStrategy
import com.vultisig.wallet.ui.models.send.submit.RedeemStrategy
import com.vultisig.wallet.ui.models.send.submit.StakeStrategy
import com.vultisig.wallet.ui.models.send.submit.UnbondStrategy
import com.vultisig.wallet.ui.models.send.submit.UnstakeStrategy
import com.vultisig.wallet.ui.models.send.submit.WithdrawUsdcCircleStrategy
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin

@Immutable
internal data class TokenBalanceUiModel(
    val model: SendSrc,
    val title: String,
    val balance: String?,
    val fiatValue: String?,
    val isNativeToken: Boolean,
    val isLayer2: Boolean,
    val tokenStandard: String?,
    val tokenLogo: ImageModel,
    @param:DrawableRes val chainLogo: Int,
)

sealed class AmountFraction(val title: UiText, val value: Float) {
    data object F25 : AmountFraction(title = "25%".asUiText(), value = 0.25f)

    data object F50 : AmountFraction(title = "50%".asUiText(), value = 0.5f)

    data object F75 : AmountFraction(title = "75%".asUiText(), value = 0.75f)

    data object F100 : AmountFraction(title = string.send_screen_max.asUiText(), value = 1f)
}

@Immutable
internal data class SendFormUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val fiatCurrency: String = "",

    // src data
    val srcAddress: String = "",
    val srcVaultName: String = "",

    // dst data
    val isDstAddressComplete: Boolean = false,

    // fees
    val totalGas: UiText = UiText.Empty,
    val gasTokenBalance: UiText? = null,
    val estimatedFee: UiText = UiText.Empty,

    // type
    val defiType: DeFiNavActions? = null,
    val slippage: String = "1.0",
    val isAutocompound: Boolean = false,

    // errors
    val errorText: UiText? = null,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val reapingError: UiText? = null,
    val bondProviderError: UiText? = null,
    val hasMemo: Boolean = false,
    val showGasFee: Boolean = true,
    val hasGasSettings: Boolean = false,
    val showGasSettings: Boolean = false,
    val specific: BlockChainSpecificAndUtxo? = null,
    val expandedSection: SendSections = SendSections.Asset,
    val usingTokenAmountInput: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAmountSelectionLoading: Boolean = false,
    val selectedAmountFraction: AmountFraction? = null,
    val amountFractionEntries: List<AmountFraction> = listOf(F25, F50, F75, F100),

    // Tron freeze/unfreeze
    val tronResourceType: TronResourceType? = null,
    val tronBalanceAvailableOverride: String? = null,
    val isTronFrozenBalancesLoading: Boolean = false,
    val hasTronFrozenBalancesError: Boolean = false,
)

internal data class SendSrc(val address: Address, val account: Account)

internal enum class SendSections {
    Asset,
    Address,
    Amount,
}

internal enum class SendFocusField {
    ADDRESS,
    AMOUNT,
}

enum class AddressBookType {
    OUTPUT,
    PROVIDER,
}

internal sealed class GasSettings {
    data class Eth(val baseFee: BigInteger, val priorityFee: BigInteger, val gasLimit: BigInteger) :
        GasSettings()

    data class UTXO(val byteFee: BigInteger) : GasSettings()
}

internal data class InvalidTransactionDataException(val text: UiText) : Exception()

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
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val requestResultRepository: RequestResultRepository,
    private val addressParserRepository: AddressParserRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val advanceGasUiRepository: AdvanceGasUiRepository,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val chainValidationService: ChainValidationService,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase,
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

    private val accounts = MutableStateFlow(emptyList<Account>())

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
            accounts = accounts,
            defiTypeProvider = { defiType },
            selectedTokenProvider = { selectedTokenValue },
            onTokenSelected = ::selectToken,
        )

    private val accountsLoader =
        AccountsLoader(
            scope = viewModelScope,
            accounts = accounts,
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

    private val sendStrategy =
        DefaultSendStrategy(
            scope = viewModelScope,
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
            vaultIdProvider = { vaultId },
            selectedAccountProvider = { selectedAccount },
            defiTypeProvider = { defiType },
            currentTronFrozenBalanceProvider = tronStakingService::currentFrozenBalance,
            navigator = navigator,
            expandSection = ::expandSection,
            emitFocusField = { field -> _focusFieldChannel.trySend(field) },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val bondStrategy =
        BondStrategy(
            scope = viewModelScope,
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
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val unbondStrategy =
        UnbondStrategy(
            scope = viewModelScope,
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
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val stakeStrategy =
        StakeStrategy(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            defiTypeProvider = { defiType },
            isAutocompoundProvider = { uiState.value.isAutocompound },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val unstakeStrategy =
        UnstakeStrategy(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            defiTypeProvider = { defiType },
            isAutocompoundProvider = { uiState.value.isAutocompound },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val mintStrategy =
        MintStrategy(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            defiTypeProvider = { defiType },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val redeemStrategy =
        RedeemStrategy(
            scope = viewModelScope,
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
            defiTypeProvider = { defiType },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
        )

    private val withdrawUsdcCircleStrategy =
        WithdrawUsdcCircleStrategy(
            scope = viewModelScope,
            tokenAmountFieldState = tokenAmountFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            mscaAddressProvider = { mscaAddress },
            showLoading = ::showLoading,
            hideLoading = ::hideLoading,
            showError = ::showError,
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

    fun selectNetwork() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            val requestId = Uuid.random().toString()

            navigator.route(
                Route.SelectNetwork(
                    vaultId = vaultId,
                    selectedNetworkId = selectedChain.id,
                    requestId = requestId,
                    filters = Route.SelectNetwork.Filters.None,
                )
            )

            updateChain(requestId = requestId, selectedChain = selectedChain)
        }
    }

    fun onNetworkLongPressStarted(position: Offset) {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            val requestId = Uuid.random().toString()

            navigator.route(
                Route.SelectNetworkPopup(
                    requestId = requestId,
                    pressX = position.x,
                    pressY = position.y,
                    vaultId = vaultId,
                    selectedNetworkId = selectedChain.id,
                    filters = Route.SelectNetwork.Filters.None,
                )
            )

            updateChain(requestId, selectedChain)
        }
    }

    private suspend fun updateChain(requestId: String, selectedChain: Chain) {
        val chain: Chain? = requestResultRepository.request(requestId)

        if (chain == null || chain == selectedChain) {
            return
        }

        val account =
            accounts.value.find { it.token.isNativeToken && it.token.chain == chain } ?: return

        selectToken(account.token)
    }

    fun openTokenSelection() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            val requestId = Uuid.random().toString()

            val selectedChain = selectedToken.value?.chain ?: Chain.ThorChain
            navigator.route(
                Route.SelectAsset(
                    vaultId = vaultId,
                    preselectedNetworkId = selectedChain.id,
                    networkFilters = Route.SelectNetwork.Filters.None,
                    requestId = requestId,
                )
            )

            val newAssetSelected = requestResultRepository.request<AssetSelected?>(requestId)
            val newToken = newAssetSelected?.token

            if (newToken != null) {
                selectToken(newToken)
                expandSection(SendSections.Address)
            }
        }
    }

    fun openTokenSelectionPopup(position: Offset) {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            val requestId = Uuid.random().toString()

            val selectedChain = selectedToken.value?.chain ?: Chain.ThorChain
            navigator.route(
                Route.SelectAssetPopup(
                    vaultId = vaultId,
                    preselectedNetworkId = selectedChain.id,
                    networkFilters = Route.SelectNetwork.Filters.None,
                    requestId = requestId,
                    pressX = position.x,
                    pressY = position.y,
                    selectedAssetId = selectedToken.value?.id.orEmpty(),
                )
            )

            val newAssetSelected = requestResultRepository.request<AssetSelected?>(requestId)
            val newToken = newAssetSelected?.token

            if (newToken != null) {
                selectToken(newToken)
                expandSection(SendSections.Address)
            }
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

                    checkChainIdExistInAccounts(
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

    private fun checkChainIdExistInAccounts(preSelectedChainIds: List<String>, vaultId: String) {
        // if chain Id is missing in accounts, add the first chain found by address manually.
        val chainIdForAddition = preSelectedChainIds.firstOrNull()
        val chainIdNotInAccounts =
            accounts.value.none { it.token.chain.id.equals(chainIdForAddition, ignoreCase = true) }
        if (!chainIdForAddition.isNullOrBlank() && chainIdNotInAccounts) {
            viewModelScope.launch {
                addNativeTokenToVault(chainIdForAddition)
                accountsLoader.load(vaultId)
            }
        }
    }

    private suspend fun addNativeTokenToVault(chainIdForAddition: ChainId) {
        val nativeToken = tokenRepository.getNativeToken(chainIdForAddition)
        val vaultId = requireNotNull(vaultId)
        val vault = requireNotNull(vaultRepository.get(vaultId))
        val (address, derivedPublicKey) =
            chainAccountAddressRepository.getAddress(coin = nativeToken, vault = vault)
        val updatedCoin = nativeToken.copy(address = address, hexPublicKey = derivedPublicKey)

        vaultRepository.addTokenToVault(vaultId, updatedCoin)
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
        viewModelScope.launch {
            isSwitchingAccounts.value = true

            uiState.update { it.copy(isAutocompound = checked) }

            val vaultId = vaultId
            if (
                (defiType == DeFiNavActions.UNSTAKE_TCY ||
                    defiType == DeFiNavActions.UNSTAKE_STCY) && vaultId != null
            ) {
                selectedToken.value = null

                if (checked) {
                    val regularAccounts =
                        accountsRepository
                            .loadAddresses(vaultId)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .first()

                    accounts.value = regularAccounts

                    delay(300)

                    regularAccounts
                        .find {
                            it.token.ticker.equals("sTCY", true) &&
                                it.token.chain == Chain.ThorChain
                        }
                        ?.let { selectToken(it.token) }
                } else {
                    val defiAccounts =
                        accountsRepository
                            .loadDeFiAddresses(vaultId, false)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .first()

                    accounts.value = defiAccounts

                    delay(300)

                    defiAccounts
                        .find {
                            it.token.ticker.equals("TCY", true) && it.token.chain == Chain.ThorChain
                        }
                        ?.let { selectToken(it.token) }
                }
                isSwitchingAccounts.value = false
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
                    checkIfTokenSelectionRequired(
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

    private fun checkIfTokenSelectionRequired(currentChain: Chain, newChain: Chain) {
        val newChainSelected = currentChain != newChain
        val isNotEvm = newChain.standard != TokenStandard.EVM
        if (newChainSelected && isNotEvm) {
            tokenPreselectionService.preSelect(
                preSelectedChainIds = listOf(newChain.id),
                preSelectedTokenId = null,
                forcePreselection = true,
            )
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
            DeFiNavActions.BOND -> bondStrategy.submit()
            DeFiNavActions.UNBOND -> unbondStrategy.submit()
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY -> stakeStrategy.submit()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.WITHDRAW_RUJI -> unstakeStrategy.submit()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> mintStrategy.submit()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> redeemStrategy.submit()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> withdrawUsdcCircleStrategy.submit()

            null,
            DeFiNavActions.DEPOSIT_USDC_CIRCLE,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.ADD_LP,
            DeFiNavActions.REMOVE_LP,
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> sendStrategy.submit()
        }
    }

    private fun hideLoading() {
        uiState.update { it.copy(isLoading = false) }
    }

    private fun showLoading() {
        uiState.update { it.copy(isLoading = true) }
    }

    private fun selectToken(token: Coin) {
        Timber.d("selectToken(token = $token)")

        // Preempt any in-flight percentage calc — otherwise it can resume after the token
        // switch and write the old token's amount into tokenAmountFieldState (e.g. the
        // autocompound toggle hits this same race that setTronResourceType already preempts).
        amountFractionManager.cancel()
        amountManager.resetUserInputCache()
        selectedToken.value = token
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
