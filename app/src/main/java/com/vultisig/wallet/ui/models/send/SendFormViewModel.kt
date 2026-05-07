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
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.blockchain.tron.TronStakingOperation
import com.vultisig.wallet.data.blockchain.tron.tronStakingMemo
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getPubKeyByChain
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
import com.vultisig.wallet.data.utils.safeLaunch
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
import com.vultisig.wallet.ui.utils.asAddressInput
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val recalculateGasFee = MutableStateFlow(0L)

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

    private var preSelectTokenJob: Job? = null
    private var loadAccountsJob: Job? = null

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
    private var loadTronFrozenBalancesJob: Job? = null

    private fun isTronStakingType(): Boolean =
        defiType == DeFiNavActions.FREEZE_TRX || defiType == DeFiNavActions.UNFREEZE_TRX

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
            currentTronFrozenBalanceProvider = ::currentTronFrozenBalance,
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
            currentTronFrozenBalanceProvider = ::currentTronFrozenBalance,
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
        calculateGasFees()
        calculateGasTokenBalance()
        collectEstimatedFee()
        collectPlanFee()
        calculateSpecific()
        collectAdvanceGasUi()
        loadVaultName()
        loadGasSettings()
        collectMaxAmount()
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

        if (this.mscaAddress != mscaAddress) {
            this.mscaAddress = mscaAddress
        }

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId
            loadAccounts(vaultId)
            loadVaultName()
            initFormType()
        }

        if (address != null) {
            setAddressFromQrCode(
                qrCode = address,
                preSelectedChainId = preSelectedChainId,
                preSelectedTokenId = preSelectedTokenId,
            )
        } else {
            preSelectToken(
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
        val initialResourceType = if (isTronStakingType()) TronResourceType.BANDWIDTH else null
        uiState.update {
            it.copy(
                defiType = this.defiType,
                isAutocompound = autoCompound,
                tronResourceType = initialResourceType,
            )
        }
        if (isTronStakingType()) {
            applyTronStakingMemo(TronResourceType.BANDWIDTH)
            if (defiType == DeFiNavActions.UNFREEZE_TRX) {
                loadTronFrozenBalances()
            }
        }
    }

    fun setTronResourceType(type: TronResourceType) {
        // Ignore toggles while a send is in flight so the captured memo/resource type
        // used by send() can't diverge from the one visible in the tab.
        if (uiState.value.isLoading) return
        if (uiState.value.tronResourceType == type) return
        uiState.update {
            it.copy(
                tronResourceType = type,
                tronBalanceAvailableOverride = null,
                selectedAmountFraction = null,
            )
        }
        applyTronStakingMemo(type)
        tokenAmountFieldState.clearText()
        fiatAmountFieldState.clearText()
        if (defiType == DeFiNavActions.UNFREEZE_TRX) {
            updateTronFrozenBalanceDisplay(type)
        }
    }

    private fun applyTronStakingMemo(type: TronResourceType) {
        val op =
            when (defiType) {
                DeFiNavActions.FREEZE_TRX -> TronStakingOperation.FREEZE
                DeFiNavActions.UNFREEZE_TRX -> TronStakingOperation.UNFREEZE
                else -> error("applyTronStakingMemo called outside Tron staking")
            }
        memoFieldState.setTextAndPlaceCursorAtEnd(tronStakingMemo(op, type))
    }

    private fun loadTronFrozenBalances() {
        loadTronFrozenBalancesJob?.cancel()
        loadTronFrozenBalancesJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Tron frozen balances")
                    setTronFrozenBalanceState(TronFrozenBalanceState.Error)
                }
            ) {
                uiState.update { it.copy(isTronFrozenBalancesLoading = true) }
                try {
                    val vault = vault ?: vaultRepository.get(vaultId ?: return@safeLaunch)
                    val trxCoin =
                        vault?.coins?.firstOrNull { it.chain == Chain.Tron && it.isNativeToken }
                    if (trxCoin == null) {
                        setTronFrozenBalanceState(TronFrozenBalanceState.Error)
                        return@safeLaunch
                    }
                    val balances = getTronFrozenBalances(trxCoin.address)
                    setTronFrozenBalanceState(TronFrozenBalanceState.Loaded(balances))
                    updateTronFrozenBalanceDisplay(
                        uiState.value.tronResourceType ?: TronResourceType.BANDWIDTH
                    )
                } finally {
                    uiState.update { it.copy(isTronFrozenBalancesLoading = false) }
                }
            }
    }

    private fun setTronFrozenBalanceState(state: TronFrozenBalanceState) {
        tronFrozenBalances.value = state
        uiState.update {
            it.copy(hasTronFrozenBalancesError = state is TronFrozenBalanceState.Error)
        }
    }

    private fun updateTronFrozenBalanceDisplay(type: TronResourceType) {
        val balances =
            (tronFrozenBalances.value as? TronFrozenBalanceState.Loaded)?.balances ?: return
        uiState.update {
            it.copy(
                tronBalanceAvailableOverride =
                    balances.forResource(type).stripTrailingZeros().toPlainString()
            )
        }
    }

    private fun currentTronFrozenBalance(): BigDecimal? {
        val type = uiState.value.tronResourceType ?: return null
        val balances =
            (tronFrozenBalances.value as? TronFrozenBalanceState.Loaded)?.balances ?: return null
        return balances.forResource(type)
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

                    preSelectToken(
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
                loadAccounts(vaultId)
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
            preSelectToken(
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

        amountManager.resetUserInputCache()
        selectedToken.value = token
    }

    private fun showError(text: UiText) {
        uiState.update { it.copy(errorText = text) }
    }

    private fun loadAccounts(vaultId: VaultId) {
        loadAccountsJob?.cancel()
        loadAccountsJob =
            when (defiType) {
                DeFiNavActions.WITHDRAW_RUJI ->
                    viewModelScope.launch { loadRewardsAccount(vaultId) }

                DeFiNavActions.WITHDRAW_USDC_CIRCLE ->
                    viewModelScope.launch { loadCircleUSDCAccount(vaultId) }

                null,
                DeFiNavActions.BOND,
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.STAKE_STCY,
                DeFiNavActions.UNSTAKE_STCY,
                DeFiNavActions.MINT_YRUNE,
                DeFiNavActions.MINT_YTCY,
                DeFiNavActions.REDEEM_YRUNE,
                DeFiNavActions.REDEEM_YTCY,
                DeFiNavActions.DEPOSIT_USDC_CIRCLE,
                DeFiNavActions.FREEZE_TRX ->
                    viewModelScope.launch {
                        accountsRepository
                            .loadAddresses(vaultId)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect(accounts)
                    }

                else ->
                    viewModelScope.launch {
                        accountsRepository
                            .loadDeFiAddresses(vaultId, false)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect(accounts)
                    }
            }
    }

    private suspend fun loadCircleUSDCAccount(vaultId: VaultId) {
        val accountsLoaded =
            accountsRepository.loadAddresses(vaultId).firstOrNull()?.flatMap { it.accounts }
        val ethereumAccount =
            accountsLoaded?.find { it.token.id.equals(Coins.Ethereum.ETH.id, true) }
                ?: Account(
                    token = Coins.Ethereum.ETH,
                    tokenValue = TokenValue(BigInteger.ZERO, Coins.Ethereum.ETH),
                    fiatValue = null,
                    price = null,
                )

        val usdc = Coins.Ethereum.USDC.copy(address = ethereumAccount.token.address)

        if (mscaAddress != null) {
            val id = usdc.generateId(mscaAddress!!)
            val cachedDetails = stakingDetailsRepository.getStakingDetailsById(vaultId, id)
            val usdcCircleAccount =
                Account(
                    token = usdc,
                    tokenValue =
                        TokenValue(
                            value = cachedDetails?.stakeAmount ?: BigInteger.ZERO,
                            token = usdc,
                        ),
                    fiatValue = null,
                    price = null,
                )
            accounts.value = listOf(ethereumAccount, usdcCircleAccount)
        } else {
            Timber.e("MSCA address not available for Circle USDC withdrawal")
            accounts.value =
                listOf(
                    ethereumAccount,
                    Account(
                        token = usdc,
                        tokenValue = TokenValue(value = BigInteger.ZERO, token = usdc),
                        fiatValue = null,
                        price = null,
                    ),
                )
        }
    }

    private suspend fun loadRewardsAccount(vaultId: VaultId) {
        val accountsLoaded =
            accountsRepository.loadAddresses(vaultId).firstOrNull()?.flatMap { it.accounts }
        val thorchainAccount =
            accountsLoaded?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) } ?: return

        val rujiAccount =
            accountsLoaded.find { it.token.id.equals(Coins.ThorChain.RUJI.id, true) } ?: return

        val cachedDetails =
            stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.ThorChain.RUJI.id)

        if (cachedDetails != null) {
            val rewardsAccount =
                Account(
                    token = RUJI_REWARDS_COIN.copy(address = thorchainAccount.token.address),
                    tokenValue =
                        TokenValue(
                            value = cachedDetails.rewards?.toBigInteger() ?: BigInteger.ZERO,
                            token = RUJI_REWARDS_COIN,
                        ),
                    fiatValue = null,
                    price = null,
                )
            accounts.value = listOf(rewardsAccount, thorchainAccount, rujiAccount)
        } else {
            accounts.value = emptyList()
        }
    }

    private fun preSelectToken(
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
        forcePreselection: Boolean = false,
    ) {
        Timber.d("preSelectToken($preSelectedChainIds, $preSelectedTokenId, $forcePreselection)")

        preSelectTokenJob?.cancel()
        preSelectTokenJob =
            viewModelScope.launch {
                accounts.collect { accounts ->
                    val preSelectedToken =
                        if (defiType == null) {
                            findPreselectedToken(accounts, preSelectedChainIds, preSelectedTokenId)
                        } else {
                            findDeFiPreselectedToken(
                                accounts,
                                preSelectedChainIds,
                                preSelectedTokenId,
                            )
                        }

                    Timber.d("Found a new token to pre select $preSelectedToken")

                    // if user hasn't yet selected any token, preselect found token
                    if (
                        (forcePreselection || selectedTokenValue == null) &&
                            preSelectedToken != null
                    ) {
                        selectToken(preSelectedToken)
                    }
                }
            }
    }

    /**
     * Returns first token found for tokenId or chainId or first token it all list, can return null
     * if there's no tokens in the vault
     */
    private fun findPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        var searchByChainResult: Coin? = null

        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id.equals(preSelectedTokenId, ignoreCase = true)) {
                // if we find token by id, return it asap
                return accountToken
            }
            if (
                searchByChainResult == null &&
                    preSelectedChainIds.contains(accountToken.chain.id) &&
                    accountToken.isNativeToken
            ) {
                // if we find token by chain, remember it and return later if nothing else found
                searchByChainResult = accountToken
            }
        }

        // if user selected none, or nothing was found, select the first token
        return searchByChainResult ?: accounts.firstOrNull()?.token
    }

    private fun findDeFiPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id.equals(preSelectedTokenId, ignoreCase = true)) {
                return accountToken
            }
        }

        // default coins, in case the account does not exist
        val defaultCoin =
            when (defiType) {
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.UNSTAKE_RUJI -> Coins.ThorChain.RUJI

                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.UNSTAKE_TCY -> Coins.ThorChain.TCY

                DeFiNavActions.MINT_YRUNE -> Coins.ThorChain.RUNE
                DeFiNavActions.MINT_YTCY -> Coins.ThorChain.TCY
                DeFiNavActions.BOND -> Coins.ThorChain.RUNE
                DeFiNavActions.UNBOND -> Coins.ThorChain.RUNE
                DeFiNavActions.WITHDRAW_RUJI -> RUJI_REWARDS_COIN
                DeFiNavActions.REDEEM_YRUNE -> Coins.ThorChain.yRUNE
                DeFiNavActions.REDEEM_YTCY -> Coins.ThorChain.yTCY
                DeFiNavActions.DEPOSIT_USDC_CIRCLE -> Coins.Ethereum.USDC
                DeFiNavActions.WITHDRAW_USDC_CIRCLE -> Coins.Ethereum.USDC
                DeFiNavActions.STAKE_STCY -> Coins.ThorChain.TCY
                DeFiNavActions.UNSTAKE_STCY -> Coins.ThorChain.sTCY
                DeFiNavActions.STAKE_CACAO,
                DeFiNavActions.UNSTAKE_CACAO,
                DeFiNavActions.ADD_LP,
                DeFiNavActions.REMOVE_LP -> Coins.MayaChain.CACAO
                DeFiNavActions.FREEZE_TRX,
                DeFiNavActions.UNFREEZE_TRX -> Coins.Tron.TRX
                null -> findPreselectedToken(accounts, preSelectedChainIds, preSelectedTokenId)
            }

        return defaultCoin
    }

    private fun calculateGasTokenBalance() {
        viewModelScope.launch {
            selectedToken
                .filterNotNull()
                .map {
                    if (it.isNativeToken) {
                        null
                    } else {
                        accounts.value
                            .find { account ->
                                account.token.isNativeToken && account.token.chain == it.chain
                            }
                            ?.tokenValue
                    }
                }
                .collect { gasTokenBalance ->
                    if (gasTokenBalance == null) {
                        uiState.update { it.copy(gasTokenBalance = null) }
                    } else {
                        uiState.update {
                            it.copy(
                                gasTokenBalance =
                                    UiText.DynamicString(mapTokenValueToString(gasTokenBalance))
                            )
                        }
                    }
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun calculateGasFees() {
        viewModelScope.launch {
            combine(
                    combine(
                            selectedToken.filterNotNull(),
                            addressFieldState.textAsFlow(),
                            memoFieldState.textAsFlow(),
                            tokenAmountFieldState.textAsFlow(),
                            recalculateGasFee,
                        ) { token, dst, memo, tokenAmount, nonce ->
                            GasFeeInput(
                                token,
                                dst.asAddressInput(),
                                memo.toString(),
                                tokenAmount,
                                nonce,
                            )
                        }
                        .debounce(350)
                        .distinctUntilChanged()
                        .mapNotNull { (token, dst, memo, tokenAmount) ->
                            val vault = vault ?: return@mapNotNull null
                            val tokenAmount = tokenAmount.toString().toBigDecimalOrNull()

                            val tokenAmountInt =
                                tokenAmount?.movePointRight(token.decimal)?.toBigInteger()
                                    ?: return@mapNotNull null

                            val chain = token.chain
                            val blockchainTransaction =
                                Transfer(
                                    coin = token,
                                    vault =
                                        VaultData(
                                            vaultHexChainCode = vault.hexChainCode,
                                            vaultHexPublicKey = vault.getPubKeyByChain(chain),
                                        ),
                                    amount = tokenAmountInt,
                                    to = addressManager.resolvedDstAddress.value ?: dst,
                                    memo = memo,
                                    isMax = false,
                                )

                            val fees =
                                withContext(Dispatchers.IO) {
                                    feeServiceComposite.calculateFees(blockchainTransaction)
                                }
                            val nativeCoin =
                                withContext(Dispatchers.IO) {
                                    tokenRepository.getNativeToken(chain.id)
                                }

                            TokenValue(value = fees.amount, token = nativeCoin)
                        }
                        .catch { Timber.e(it) },
                    gasSettings,
                    specific,
                ) { gasFee, gasSettings, specific ->
                    this@SendFormViewModel.gasFee.value =
                        adjustGasFee(gasFee, gasSettings, specific)
                }
                .collect()
        }
    }

    private fun collectPlanFee() {
        viewModelScope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    addressFieldState.textAsFlow(),
                    tokenAmountFieldState.textAsFlow(),
                    specific.filterNotNull(),
                    memoFieldState.textAsFlow(),
                ) { token, dstAddress, tokenAmount, specific, memo ->
                    PlanFeeInput(token, dstAddress, tokenAmount, specific, memo)
                }
                .combine(recalculateGasFee) { data, _ -> data }
                .mapNotNull { (token, dstAddress, tokenAmount, specific, memo) ->
                    try {
                        val chain = token.chain
                        if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano) {
                            planFee.value = 1
                            return@mapNotNull null
                        }

                        val vaultId =
                            vaultId
                                ?: throw InvalidTransactionDataException(
                                    UiText.StringResource(string.send_error_no_token)
                                )

                        val resolvedDstAddress =
                            addressParserRepository.resolveName(dstAddress.asAddressInput(), chain)
                        val tokenAmountInt =
                            tokenAmount
                                .toString()
                                .toBigDecimal()
                                .movePointRight(token.decimal)
                                .toBigInteger()

                        val plan =
                            bitcoinPlanService.getPlan(
                                vaultId,
                                token,
                                resolvedDstAddress,
                                tokenAmountInt,
                                specific,
                                memo.toString(),
                            )

                        planFee.value = plan.fee
                        planBtc.value = plan
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
                .collect()
        }
    }

    private fun collectMaxAmount() {
        viewModelScope.launch {
            amountManager.isMaxAmount.collect { isMax ->
                val chain = selectedAccount?.token?.chain ?: return@collect
                // Only require to re-trigger utxo chains, due to no change output utxo and
                // therefore
                // less fees
                if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                    val spec =
                        specific.value?.blockChainSpecific as? BlockChainSpecific.UTXO
                            ?: return@collect
                    val updatedSpec =
                        specific.value?.copy(blockChainSpecific = spec.copy(sendMaxAmount = isMax))
                    specific.value = updatedSpec
                }
            }
        }
    }

    private fun collectEstimatedFee() {
        viewModelScope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    gasFee.filterNotNull(),
                    gasSettings,
                    planFee.filterNotNull(),
                ) { token, gasFee, gasSettings, planFee ->
                    val chain = token.chain
                    val evmGasSettings = gasSettings as? GasSettings.Eth
                    val estimatedFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit =
                                    if (evmGasSettings != null) evmGasSettings.gasLimit
                                    else BigInteger.valueOf(1),
                                gasFee =
                                    selectGasFeeForFeeEstimation(
                                        chain = chain,
                                        gasFee = gasFee,
                                        planFee = planFee,
                                        evmGasSettings = evmGasSettings,
                                    ),
                                selectedToken = token,
                                perUnit = true,
                            )
                        )

                    uiState.update {
                        it.copy(
                            estimatedFee = UiText.DynamicString(estimatedFee.formattedFiatValue),
                            totalGas = UiText.DynamicString(estimatedFee.formattedTokenValue),
                        )
                    }
                }
                .collect()
        }
    }

    @OptIn(FlowPreview::class)
    private fun calculateSpecific() {
        viewModelScope.launch {
            // dstAddress is forwarded to getSpecific() for every chain so that
            // TRON fee estimation can account for bandwidth delegated to the receiver.
            // Recomputing specifics on every keystroke would be wasteful, so the
            // address is debounced before triggering the recalculation.
            val dstAddressFlow =
                addressFieldState
                    .textAsFlow()
                    .map { it.toString().asAddressInput() }
                    .debounce(300)
                    .distinctUntilChanged()

            combine(selectedToken.filterNotNull(), gasFee.filterNotNull(), dstAddressFlow) {
                    token,
                    gasFee,
                    dstAddress ->
                    val chain = token.chain
                    val srcAddress = token.address
                    advanceGasUiRepository.updateTokenStandard(token.chain.standard)

                    val validDstAddress =
                        dstAddress.takeIf {
                            it.isNotBlank() && chainAccountAddressRepository.isValid(chain, it)
                        }

                    try {
                        val spec =
                            blockChainSpecificRepository.getSpecific(
                                chain,
                                srcAddress,
                                token,
                                gasFee,
                                isSwap = false,
                                isMaxAmountEnabled = false,
                                isDeposit = false,
                                dstAddress = validDstAddress,
                            )
                        specific.value = spec
                        advanceGasUiRepository.updateBlockChainSpecific(spec.blockChainSpecific)
                        advanceGasUiRepository.showIcon()
                        uiState.update { it.copy(specific = spec) }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
                .collect()
        }
    }

    private fun adjustGasFee(
        gasFee: TokenValue,
        gasSettings: GasSettings?,
        spec: BlockChainSpecificAndUtxo?,
    ) =
        gasFee.copy(
            value =
                if (
                    gasSettings is GasSettings.UTXO &&
                        spec?.blockChainSpecific is BlockChainSpecific.UTXO
                ) {
                    gasSettings.byteFee
                } else gasFee.value
        )

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

    fun refreshGasFee() {
        viewModelScope.launch {
            uiState.update { it.copy(isRefreshing = true) }
            recalculateGasFee.update { it + 1 }
            // Rapid toggling of isRefreshing can cause the initial true value to be skipped,
            // displaying only the false value in the UI resulting in the swipe refresh being
            // frozen.
            // this line prevent missing true value in these cases.
            delay(100)
            uiState.update { it.copy(isRefreshing = false) }
        }
    }
}

private data class GasFeeInput(
    val token: Coin,
    val dst: String,
    val memo: String,
    val tokenAmount: CharSequence,
    val nonce: Long = 0,
)

private data class PlanFeeInput(
    val token: Coin,
    val dstAddress: CharSequence,
    val tokenAmount: CharSequence,
    val specific: BlockChainSpecificAndUtxo,
    val memo: CharSequence,
)
