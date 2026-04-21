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
import com.vultisig.wallet.R
import com.vultisig.wallet.R.string
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.blockchain.tron.TRON_STAKING_MEMO_REGEX
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.chains.helpers.ThorchainFunctions
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositMemo.Bond
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.OPERATION_CIRCLE_WITHDRAW
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.allowZeroGas
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.toValue
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
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.AmountFraction.F100
import com.vultisig.wallet.ui.models.send.AmountFraction.F25
import com.vultisig.wallet.ui.models.send.AmountFraction.F50
import com.vultisig.wallet.ui.models.send.AmountFraction.F75
import com.vultisig.wallet.ui.models.send.usecases.CalculationUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_RUJI_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_TCY_COMPOUND_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_YTCY_AFFILIATE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YTCY_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asAddressInput
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import vultisig.keysign.v1.TransactionType
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
    @DrawableRes val chainLogo: Int,
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
)

internal data class SendSrc(val address: Address, val account: Account)

internal enum class SendSections {
    Asset,
    Address,
    Amount,
    BondAddress,
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
    private val calculationUseCase: CalculationUseCase,
    private val chainValidationService: ChainValidationService,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
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

    private var chooseAmountFractionJob: Job? = null

    private val appCurrency =
        appCurrencyRepository.currency.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    private val planFee = MutableStateFlow<Long?>(null)
    private val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val resolvedDstAddress = MutableStateFlow<String?>(null)
    private val dstAddressLabel = MutableStateFlow<String?>(null)

    private var gasSettings = MutableStateFlow<GasSettings?>(null)

    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)
    private var maxAmount = BigDecimal.ZERO
    private val isMaxAmount = MutableStateFlow(false)

    private var lastTokenValueUserInput = ""
    private var lastFiatValueUserInput = ""

    private val isSwitchingAccounts = MutableStateFlow(false)

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
        collectAmountChanges()
        calculateGasFees()
        calculateGasTokenBalance()
        collectEstimatedFee()
        collectPlanFee()
        calculateSpecific()
        collectAdvanceGasUi()
        collectAmountChecks()
        loadVaultName()
        loadGasSettings()
        collectDstAddress()
        collectAddress()
        collectMaxAmount()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun collectAddress() {
        viewModelScope.launch {
            addressFieldState
                .textAsFlow()
                .debounce(300)
                .combine(selectedToken.filterNotNull()) { address, token ->
                    address.asAddressInput() to token
                }
                .mapLatest { (addressStr, token) ->
                    if (chainAccountAddressRepository.isValid(token.chain, addressStr)) {
                        // Only clear ENS label if the user typed a new raw address,
                        // not when we programmatically set the field to the resolved address.
                        if (addressStr != resolvedDstAddress.value) {
                            dstAddressLabel.value = null
                        }
                        resolvedDstAddress.value = addressStr
                        expandSection(SendSections.Amount)
                    } else if (addressStr.isNotEmpty()) {
                        // Clear stale resolved address while async resolution is in-flight
                        resolvedDstAddress.value = null
                        dstAddressLabel.value = null
                        try {
                            val resolved =
                                addressParserRepository.resolveName(addressStr, token.chain)
                            // Ignore stale result if user changed input while resolving
                            if (addressFieldState.text.asAddressInput() != addressStr)
                                return@mapLatest
                            if (chainAccountAddressRepository.isValid(token.chain, resolved)) {
                                dstAddressLabel.value = addressStr
                                resolvedDstAddress.value = resolved
                                addressFieldState.setTextAndPlaceCursorAtEnd(resolved)
                                expandSection(SendSections.Amount)
                            } else {
                                resolvedDstAddress.value = null
                                dstAddressLabel.value = null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            resolvedDstAddress.value = null
                            dstAddressLabel.value = null
                        }
                    } else {
                        resolvedDstAddress.value = null
                        dstAddressLabel.value = null
                    }
                }
                .collect()
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
        uiState.update { it.copy(defiType = this.defiType, isAutocompound = autoCompound) }
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

    private fun collectDstAddress() {
        viewModelScope.launch {
            addressFieldState
                .textAsFlow()
                .map { it.toString() }
                .collect { dstAddress ->
                    val isDstAddressComplete = dstAddress.isNotBlank()
                    uiState.update { it.copy(isDstAddressComplete = isDstAddressComplete) }
                }
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
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
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
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

    fun chooseMaxTokenAmount() {
        chooseAmountFractionJob?.cancel()
        chooseAmountFractionJob =
            viewModelScope.launch {
                uiState.update {
                    it.copy(selectedAmountFraction = F100, isAmountSelectionLoading = true)
                }
                val amount =
                    try {
                        calculatePercentageWithAccurateFee(1f)
                    } finally {
                        uiState.update { it.copy(isAmountSelectionLoading = false) }
                    }
                maxAmount = amount
                isMaxAmount.value = true
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amount.toPlainString())
            }
    }

    fun choosePercentageAmount(amountFraction: AmountFraction) {
        chooseAmountFractionJob?.cancel()
        chooseAmountFractionJob =
            viewModelScope.launch {
                uiState.update {
                    it.copy(
                        selectedAmountFraction = amountFraction,
                        isAmountSelectionLoading = true,
                    )
                }
                val amount =
                    try {
                        calculatePercentageWithAccurateFee(amountFraction.value)
                    } finally {
                        uiState.update { it.copy(isAmountSelectionLoading = false) }
                    }
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amount.toPlainString())
            }
    }

    private suspend fun calculatePercentageWithAccurateFee(percentage: Float): BigDecimal {
        val result =
            calculationUseCase.calculatePercentageWithAccurateFee(
                percentage = percentage,
                vault = vault,
                selectedAccount = selectedAccount,
                gasFee = gasFee.value,
                gasSettings = gasSettings,
                specific = specific,
                defiType = defiType,
                addressText = addressFieldState.text.toString(),
                memoText = memoFieldState.text.toString(),
            )
        result.updatedGasFee?.let { gasFee.value = it }
        return result.amount
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun onClickContinue() {
        when (uiState.value.defiType) {
            DeFiNavActions.BOND -> bond()
            DeFiNavActions.UNBOND -> unbond()
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.STAKE_STCY -> stake()

            DeFiNavActions.UNSTAKE_RUJI,
            DeFiNavActions.UNSTAKE_TCY,
            DeFiNavActions.UNSTAKE_STCY,
            DeFiNavActions.WITHDRAW_RUJI -> unstake()

            DeFiNavActions.MINT_YRUNE,
            DeFiNavActions.MINT_YTCY -> mint()

            DeFiNavActions.REDEEM_YRUNE,
            DeFiNavActions.REDEEM_YTCY -> redeem()

            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> withDrawUSDCCircle()
            else -> send()
        }
    }

    private fun withDrawUSDCCircle() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }
                val nonDeFiAccount =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.Ethereum.ETH.id, true) }

                val nonDeFiBalance = nonDeFiAccount?.tokenValue?.value ?: BigInteger.ZERO

                if (nonDeFiBalance < gasFee.value) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val memo =
                    EthereumFunction.withdrawCircleMSCA(
                        vaultAddress =
                            nonDeFiAccount?.token?.address ?: error("Vault Address Empty"),
                        tokenAddress = Coins.Ethereum.USDC.contractAddress,
                        amount = tokenAmountInt,
                    )

                val specific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository.getSpecific(
                            chain,
                            srcAddress,
                            selectedToken,
                            gasFee,
                            isSwap = false,
                            isMaxAmountEnabled = false,
                            isDeposit = true,
                        )
                    }

                val nativeCoin = nonDeFiAccount.token

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = nativeCoin,
                        srcAddress = srcAddress,
                        dstAddress =
                            mscaAddress
                                ?: throw InvalidTransactionDataException(
                                    UiText.StringResource(R.string.send_error_msca_not_deployed)
                                ),
                        memo = memo,
                        srcTokenValue = TokenValue(value = BigInteger.ZERO, token = nativeCoin),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                        operation = OPERATION_CIRCLE_WITHDRAW,
                    )

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    fun send() {
        if (addressFieldState.text.isBlank()) {
            expandSection(SendSections.Address)
            _focusFieldChannel.trySend(SendFocusField.ADDRESS)
            return
        }
        if (tokenAmountFieldState.text.isBlank()) {
            expandSection(SendSections.Amount)
            _focusFieldChannel.trySend(SendFocusField.AMOUNT)
            return
        }

        viewModelScope.launch {
            showLoading()
            try {
                val vaultId =
                    vaultId
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                val selectedAccount =
                    selectedAccount
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                val chain = selectedAccount.token.chain

                if (
                    !chainAccountAddressRepository.isValid(
                        chain,
                        addressFieldState.text.asAddressInput(),
                    )
                ) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val gasFee = awaitGasFee()

                if (!selectedAccount.token.allowZeroGas() && gasFee.value <= BigInteger.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )
                }
                val rawInput = addressFieldState.text.asAddressInput()
                val dstAddress =
                    try {
                        addressParserRepository.resolveName(rawInput, chain)
                    } catch (e: Exception) {
                        Timber.e(e)
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.failed_to_resolve_address)
                        )
                    }
                // Use the label from collectAddress() if available (ENS/thorname was already
                // resolved
                // and the field was rewritten to the resolved address). Fall back to rawInput for
                // cases where the user typed an ENS name and taps send before debounce completes.
                val labelCandidate = dstAddressLabel.value ?: rawInput
                val dstLabel =
                    labelCandidate.takeIf {
                        it.isNotBlank() &&
                            !chainAccountAddressRepository.isValid(chain, it) &&
                            chainAccountAddressRepository.isValid(chain, dstAddress)
                    }

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val selectedTokenValue =
                    selectedAccount.tokenValue
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                val memo = memoFieldState.text.toString().takeIf { it.isNotEmpty() }

                val selectedToken = selectedAccount.token

                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val srcAddress = selectedToken.address
                val isMaxAmount = tokenAmount == maxAmount

                if (chain == Chain.Tron) {
                    val isTronStakingOp =
                        memo != null &&
                            selectedToken.isNativeToken &&
                            TRON_STAKING_MEMO_REGEX.matches(memo)
                    if (!isTronStakingOp && srcAddress == dstAddress) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_same_address)
                        )
                    }
                }

                val specific =
                    blockChainSpecificRepository
                        .getSpecific(
                            chain = chain,
                            address = srcAddress,
                            token = selectedToken,
                            gasFee = gasFee,
                            memo = memoFieldState.text.toString().takeIf { it.isNotEmpty() },
                            tokenAmountValue = tokenAmountInt,
                            isSwap = false,
                            isMaxAmountEnabled = isMaxAmount,
                            isDeposit = false,
                            dstAddress = dstAddress,
                        )
                        .let {
                            val gasSettings = gasSettings.value
                            if (gasSettings != null) {
                                val spec = it.blockChainSpecific

                                when {
                                    gasSettings is GasSettings.Eth &&
                                        spec is BlockChainSpecific.Ethereum -> {
                                        it.copy(
                                            blockChainSpecific =
                                                spec.copy(
                                                    maxFeePerGasWei = gasSettings.baseFee,
                                                    priorityFeeWei = gasSettings.priorityFee,
                                                    gasLimit = gasSettings.gasLimit,
                                                )
                                        )
                                    }

                                    gasSettings is GasSettings.UTXO &&
                                        spec is BlockChainSpecific.UTXO -> {
                                        it.copy(
                                            blockChainSpecific =
                                                spec.copy(byteFee = gasSettings.byteFee)
                                        )
                                    }

                                    else -> it
                                }
                            } else {
                                it
                            }
                        }
                        .let { specific ->
                            if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                                planBtc.value
                                    ?: calculationUseCase
                                        .getBitcoinTransactionPlan(
                                            vaultId = vaultId,
                                            selectedToken = selectedToken,
                                            dstAddress = dstAddress,
                                            tokenAmountInt = tokenAmountInt,
                                            specific = specific,
                                            memo = memo,
                                        )
                                        .also { plan ->
                                            planBtc.value = plan
                                            planFee.value = plan.fee
                                        }

                                val selectedSpecific =
                                    chainValidationService.selectUtxosIfNeeded(
                                        chain = chain,
                                        specific = specific,
                                        plan = planBtc.value,
                                    )
                                selectedSpecific
                            } else {
                                specific
                            }
                        }

                if (selectedToken.isNativeToken) {
                    val availableTokenBalance =
                        getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                            ?: BigInteger.ZERO

                    if (tokenAmountInt > availableTokenBalance) {
                        throw InvalidTransactionDataException(
                            UiText.FormattedText(
                                R.string.send_error_insufficient_native_balance_with_fees,
                                listOf(selectedToken.ticker),
                            )
                        )
                    }

                    if (chain == Chain.Cardano) {
                        chainValidationService.validateCardanoUTXORequirements(
                            sendAmount = tokenAmountInt,
                            totalBalance = selectedTokenValue.value,
                            estimatedFee = gasFee.value,
                        )
                    }

                    if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                        chainValidationService.validateBtcLikeAmount(
                            tokenAmountInt,
                            chain,
                            planBtc.value,
                        )
                    }
                } else {
                    val nativeTokenAccount =
                        accounts.value.find { it.token.isNativeToken && it.token.chain == chain }
                    val nativeTokenValue =
                        nativeTokenAccount?.tokenValue?.value
                            ?: throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.send_error_no_token)
                            )

                    if (selectedTokenValue.value < tokenAmountInt) {
                        throw InvalidTransactionDataException(
                            UiText.FormattedText(
                                R.string.send_error_insufficient_native_balance_with_fees,
                                listOf(selectedToken.ticker),
                            )
                        )
                    } else if (nativeTokenValue < gasFee.value) {
                        throw InvalidTransactionDataException(
                            UiText.FormattedText(
                                R.string.insufficient_native_token,
                                listOf(nativeTokenAccount.token.ticker),
                            )
                        )
                    }
                }

                val evmGasSettings = gasSettings.value as? GasSettings.Eth
                val totalGasAndFee =
                    gasFeeToEstimatedFee(
                        GasFeeParams(
                            gasLimit =
                                if (evmGasSettings != null) evmGasSettings.gasLimit
                                else BigInteger.valueOf(1),
                            gasFee =
                                selectGasFeeForFeeEstimation(
                                    chain = chain,
                                    gasFee = gasFee,
                                    planFee = planFee.value,
                                    evmGasSettings = evmGasSettings,
                                ),
                            selectedToken = selectedToken,
                        )
                    )

                val transaction =
                    Transaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        chainId = chain.raw,
                        token = selectedToken,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                        dstLabel = dstLabel,
                        tokenValue =
                            TokenValue(
                                value = tokenAmountInt,
                                unit = selectedTokenValue.unit,
                                decimals = selectedToken.decimal,
                            ),
                        fiatValue =
                            FiatValue(
                                value =
                                    fiatAmountFieldState.text.toString().toBigDecimalOrNull()
                                        ?: BigDecimal.ZERO,
                                currency = appCurrency.value.ticker,
                            ),
                        gasFee = gasFee,
                        blockChainSpecific = specific.blockChainSpecific,
                        utxos = specific.utxos,
                        memo = memo,
                        estimatedFee = totalGasAndFee.formattedFiatValue,
                        totalGas = totalGasAndFee.formattedTokenValue,
                    )

                transactionRepository.addTransaction(transaction)

                navigator.route(Route.VerifySend(transactionId = transaction.id, vaultId = vaultId))
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    fun bond() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                val providerAddress =
                    if (providerBondFieldState.text.toString().isNotEmpty()) {
                        try {
                            addressParserRepository.resolveName(
                                providerBondFieldState.text.toString(),
                                chain,
                            )
                        } catch (e: Exception) {
                            Timber.e(e)
                            throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.failed_to_resolve_address)
                            )
                        }
                    } else {
                        ""
                    }

                val feeBondOperator = operatorFeesBondFieldState.text.toString()

                // Validate operator fee is a valid integer (basis points)
                val operatorFeeValue: Int? =
                    if (feeBondOperator.isNotEmpty()) {
                        feeBondOperator.toIntOrNull()?.takeIf {
                            it in 0..10000
                        } // Basis points: 0-10000 (0-100%)
                        ?: throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.send_error_invalid_operator_fee)
                            )
                    } else {
                        null
                    }

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val depositMemo =
                    Bond.Thor(
                        nodeAddress = dstAddress,
                        providerAddress = providerAddress.takeIf { it.isNotEmpty() },
                        operatorFee = operatorFeeValue,
                    )

                val specific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository.getSpecific(
                            chain,
                            srcAddress,
                            selectedToken,
                            gasFee,
                            isSwap = false,
                            isMaxAmountEnabled = false,
                            isDeposit = true,
                        )
                    }

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = selectedToken,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                        memo = depositMemo.toString(),
                        srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                    )

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    fun unbond() {
        viewModelScope.launch {
            try {
                showLoading()
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                val providerAddress =
                    if (providerBondFieldState.text.toString().isNotEmpty()) {
                        try {
                            addressParserRepository.resolveName(
                                providerBondFieldState.text.toString(),
                                chain,
                            )
                        } catch (e: Exception) {
                            Timber.e(e)
                            throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.failed_to_resolve_address)
                            )
                        }
                    } else {
                        ""
                    }

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val selectedToken = selectedAccount.token
                val selectedAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, BigInteger.ZERO)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                // Get Token Balance normal and check there is for fees
                val depositMemo =
                    DepositMemo.Unbond.Thor(
                        nodeAddress = dstAddress,
                        srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
                        providerAddress = providerAddress.takeIf { it.isNotEmpty() },
                    )

                val specific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository.getSpecific(
                            chain,
                            selectedAddress,
                            selectedToken,
                            gasFee,
                            isSwap = false,
                            isMaxAmountEnabled = false,
                            isDeposit = true,
                        )
                    }

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = selectedToken,
                        srcAddress = selectedAddress,
                        dstAddress = dstAddress,
                        memo = depositMemo.toString(),
                        srcTokenValue =
                            TokenValue(
                                value =
                                    (chain == Chain.MayaChain).let {
                                        if (it) 1.toBigInteger() else BigInteger.ZERO
                                    },
                                token = selectedToken,
                            ),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                    )

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    private fun stake() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val nonDeFiBalance =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                        ?.tokenValue
                        ?.value ?: BigInteger.ZERO

                if (nonDeFiBalance < gasFee.value) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val depositTx =
                    when (defiType) {
                        DeFiNavActions.STAKE_RUJI ->
                            createRujiStakeDepositTransaction(
                                vaultId = vaultId,
                                selectedToken = selectedToken,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                                gasFee = gasFee,
                                chain = chain,
                            )

                        DeFiNavActions.STAKE_TCY,
                        DeFiNavActions.STAKE_STCY ->
                            createTCYStakeDepositTransaction(
                                vaultId = vaultId,
                                selectedToken = selectedToken,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                                gasFee = gasFee,
                                chain = chain,
                            )

                        else -> error("DeFi Type not supported ${defiType?.type}")
                    }

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    fun unstake() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val nonDeFiBalance =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                        ?.tokenValue
                        ?.value ?: BigInteger.ZERO

                if (nonDeFiBalance < gasFee.value) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val depositTx =
                    when (defiType) {
                        DeFiNavActions.UNSTAKE_RUJI ->
                            createRUJIUnstakeDepositTransaction(
                                vaultId = vaultId,
                                selectedToken = selectedToken,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                                gasFee = gasFee,
                                chain = chain,
                            )

                        DeFiNavActions.UNSTAKE_TCY,
                        DeFiNavActions.UNSTAKE_STCY ->
                            createYTCUnstakeDepositTransaction(
                                vaultId = vaultId,
                                selectedToken = selectedToken,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                                totalTokenAmount = availableTokenBalance,
                                gasFee = gasFee,
                                chain = chain,
                            )

                        DeFiNavActions.WITHDRAW_RUJI -> {
                            val ruji =
                                accountsRepository
                                    .loadAddresses(vaultId)
                                    .firstOrNull()
                                    ?.flatMap { it.accounts }
                                    ?.find { it.token.id.equals(Coins.ThorChain.RUJI.id, true) }
                                    ?: return@launch

                            createRUJIRewardsDepositTransaction(
                                vaultId = vaultId,
                                selectedToken = ruji.token,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                                gasFee = gasFee,
                                chain = chain,
                            )
                        }

                        else -> error("DeFi Type not supported ${defiType?.type}")
                    }

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    private fun mint() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val nonDeFiBalance =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                        ?.tokenValue
                        ?.value ?: BigInteger.ZERO

                if (nonDeFiBalance < gasFee.value) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val depositMemo = "receive:${selectedToken.ticker.lowercase()}:$tokenAmount"

                val specific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository.getSpecific(
                            chain,
                            srcAddress,
                            selectedToken,
                            gasFee,
                            isSwap = false,
                            isMaxAmountEnabled = false,
                            isDeposit = true,
                            transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                        )
                    }

                val tokenContract =
                    when (uiState.value.defiType) {
                        DeFiNavActions.MINT_YRUNE -> {
                            YRUNE_CONTRACT
                        }

                        DeFiNavActions.MINT_YTCY -> {
                            YTCY_CONTRACT
                        }

                        else -> {
                            throw RuntimeException("Invalid Deposit Parameter ")
                        }
                    }

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = selectedToken,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                        memo = depositMemo,
                        srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                        wasmExecuteContractPayload =
                            ThorchainFunctions.mintYToken(
                                fromAddress = srcAddress,
                                stakingContract = YRUNE_YTCY_AFFILIATE_CONTRACT,
                                tokenContract = tokenContract,
                                denom = selectedToken.ticker.lowercase(),
                                amount = tokenAmountInt,
                            ),
                    )

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    private fun redeem() {
        viewModelScope.launch {
            showLoading()
            try {
                val accountValidation = accountValidation()
                val vaultId = accountValidation.vaultId
                val chain = accountValidation.chain
                val dstAddress = accountValidation.dstAddress
                val selectedAccount = accountValidation.selectedAccount
                val gasFee = accountValidation.gasFee

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val nonDeFiBalance =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                        ?.tokenValue
                        ?.value ?: BigInteger.ZERO

                if (nonDeFiBalance < gasFee.value) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }

                val selectedToken = selectedAccount.token
                val srcAddress = selectedToken.address
                val tokenAmountInt =
                    tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                val availableTokenBalance =
                    getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                        ?: BigInteger.ZERO

                if (tokenAmountInt > availableTokenBalance) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.send_error_insufficient_native_balance_with_fees,
                            listOf(selectedToken.ticker),
                        )
                    )
                }

                val depositMemo = "sell:${selectedToken.contractAddress}:$tokenAmount"

                val specific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository.getSpecific(
                            chain,
                            srcAddress,
                            selectedToken,
                            gasFee,
                            isSwap = false,
                            isMaxAmountEnabled = false,
                            isDeposit = true,
                            transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                        )
                    }

                val tokenContract =
                    when (uiState.value.defiType) {
                        DeFiNavActions.REDEEM_YRUNE -> {
                            YRUNE_CONTRACT
                        }

                        DeFiNavActions.REDEEM_YTCY -> {
                            YTCY_CONTRACT
                        }

                        else -> {
                            throw RuntimeException("Invalid Deposit Parameter ")
                        }
                    }

                val slippage = slippageFieldState.text.toString()
                val slippageValidation = chainValidationService.validateSlippage(slippage)
                if (slippageValidation != null) {
                    throw InvalidTransactionDataException(slippageValidation)
                }

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = selectedToken,
                        srcAddress = srcAddress,
                        dstAddress = tokenContract,
                        memo = depositMemo,
                        srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                        wasmExecuteContractPayload =
                            ThorchainFunctions.redeemYToken(
                                fromAddress = srcAddress,
                                tokenContract = tokenContract,
                                slippage = chainValidationService.formatSlippage(slippage),
                                denom = selectedToken.contractAddress,
                                amount = tokenAmountInt,
                            ),
                    )

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }

    private suspend fun getFeesFiatValue(gasFee: TokenValue, selectedToken: Coin): EstimatedGasFee {
        return gasFeeToEstimatedFee(
            GasFeeParams(BigInteger.valueOf(1), gasFee = gasFee, selectedToken = selectedToken)
        )
    }

    @kotlin.ExperimentalStdlibApi
    private fun selectUtxosIfNeeded(
        chain: Chain,
        specific: BlockChainSpecificAndUtxo,
    ): BlockChainSpecificAndUtxo {
        specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        val updatedUtxo =
            planBtc.value?.utxosOrBuilderList?.map { planUtxo ->
                UtxoInfo(
                    hash = planUtxo.outPoint.hash.toByteArray().reversedArray().toHexString(),
                    index = planUtxo.outPoint.index.toUInt(),
                    amount = planUtxo.amount,
                )
            } ?: return specific

        return specific.copy(utxos = updatedUtxo)
    }

    private fun validateBtcLikeAmount(tokenAmountInt: BigInteger, chain: Chain) {
        val minAmount = chain.getDustThreshold
        if (tokenAmountInt < minAmount) {
            val symbol = chain.coinType.symbol
            val name = chain.raw
            val formattedMinAmount = chain.toValue(minAmount).toString()
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.send_form_minimum_send_amount_is_requires_this,
                    listOf(formattedMinAmount, symbol, name),
                )
            )
        }

        if (planBtc.value?.error != SigningError.OK) {
            throw InvalidTransactionDataException(R.string.insufficient_utxos_error.asUiText())
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

        lastTokenValueUserInput = ""
        selectedToken.value = token
    }

    private fun showError(text: UiText) {
        uiState.update { it.copy(errorText = text) }
    }

    private suspend fun awaitGasFee(): TokenValue {
        // Return cached value immediately if the background debounce already resolved
        gasFee.value?.let {
            return it
        }

        // Wait for the background calculateGasFees() debounce to emit.
        // If it doesn't resolve (e.g. RPC error swallowed by the flow), throw
        // the localized gas fee error so the user can retry.
        try {
            return withTimeout(GAS_FEE_TIMEOUT_MS) { gasFee.filterNotNull().first() }
        } catch (_: TimeoutCancellationException) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_gas_fee)
            )
        }
    }

    private fun loadAccounts(vaultId: VaultId) {
        loadAccountsJob?.cancel()
        loadAccountsJob =
            if (
                this.defiType == null ||
                    this.defiType == DeFiNavActions.BOND ||
                    this.defiType == DeFiNavActions.STAKE_RUJI ||
                    this.defiType == DeFiNavActions.STAKE_TCY ||
                    this.defiType == DeFiNavActions.STAKE_STCY ||
                    this.defiType == DeFiNavActions.UNSTAKE_STCY ||
                    this.defiType == DeFiNavActions.MINT_YRUNE ||
                    this.defiType == DeFiNavActions.MINT_YTCY ||
                    this.defiType == DeFiNavActions.REDEEM_YRUNE ||
                    this.defiType == DeFiNavActions.REDEEM_YTCY ||
                    this.defiType == DeFiNavActions.DEPOSIT_USDC_CIRCLE
            ) {
                viewModelScope.launch {
                    accountsRepository
                        .loadAddresses(vaultId)
                        .map { addrs -> addrs.flatMap { it.accounts } }
                        .collect(accounts)
                }
            } else if (this.defiType == DeFiNavActions.WITHDRAW_RUJI) {
                viewModelScope.launch { loadRewardsAccount(vaultId) }
            } else if (this.defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE) {
                viewModelScope.launch { loadCircleUSDCAccount(vaultId) }
            } else {
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
            calculationUseCase.gasTokenBalanceFlow(selectedToken, accounts).collect {
                gasTokenBalance ->
                uiState.update { it.copy(gasTokenBalance = gasTokenBalance) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun calculateGasFees() {
        viewModelScope.launch {
            calculationUseCase
                .gasFeesFlow(
                    selectedToken = selectedToken,
                    addressFlow = addressFieldState.textAsFlow(),
                    memoFlow = memoFieldState.textAsFlow(),
                    tokenAmountFlow = tokenAmountFieldState.textAsFlow(),
                    gasSettings = gasSettings,
                    specific = specific,
                    resolvedDstAddress = resolvedDstAddress,
                    vault = { vault },
                    recalculate = recalculateGasFee,
                )
                .collect { fee -> gasFee.value = fee }
        }
    }

    private fun collectPlanFee() {
        viewModelScope.launch {
            calculationUseCase
                .planFeeFlow(
                    selectedToken = selectedToken,
                    addressFlow = addressFieldState.textAsFlow(),
                    tokenAmountFlow = tokenAmountFieldState.textAsFlow(),
                    specific = specific,
                    memoFlow = memoFieldState.textAsFlow(),
                    vaultId = { vaultId },
                )
                .collect { result ->
                    result ?: return@collect
                    planFee.value = result.planFee
                    planBtc.value = result.planBtc
                }
        }
    }

    private fun collectMaxAmount() {
        viewModelScope.launch {
            calculationUseCase
                .maxAmountSpecificFlow(
                    isMaxAmount = isMaxAmount,
                    selectedAccount = { selectedAccount },
                    specific = specific,
                )
                .collect { updatedSpec -> specific.value = updatedSpec }
        }
    }

    private fun collectEstimatedFee() {
        viewModelScope.launch {
            calculationUseCase
                .estimatedFeeFlow(
                    selectedToken = selectedToken,
                    gasFee = gasFee,
                    gasSettings = gasSettings,
                    planFee = planFee,
                )
                .collect { result ->
                    uiState.update {
                        it.copy(
                            estimatedFee = UiText.DynamicString(result.estimatedFee),
                            totalGas = UiText.DynamicString(result.totalGas),
                        )
                    }
                }
        }
    }

    private fun calculateSpecific() {
        viewModelScope.launch {
            combine(selectedToken.filterNotNull(), gasFee.filterNotNull()) { token, gasFee ->
                    val chain = token.chain
                    val srcAddress = token.address
                    advanceGasUiRepository.updateTokenStandard(token.chain.standard)

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

    private fun collectAmountChanges() {
        viewModelScope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    tokenAmountFieldState.textAsFlow(),
                    fiatAmountFieldState.textAsFlow(),
                ) { selectedToken, tokenFieldValue, fiatFieldValue ->
                    val tokenString = tokenFieldValue.toString()
                    val fiatString = fiatFieldValue.toString()
                    if (lastTokenValueUserInput != tokenString) {
                        val tokenDecimal = tokenString.toBigDecimalOrNull()
                        isMaxAmount.value = tokenDecimal == maxAmount && maxAmount > BigDecimal.ZERO

                        val fiatValue =
                            convertValue(tokenString, selectedToken) { value, price, token ->
                                    // this is the fiat value , we should not keep too much decimal
                                    // places
                                    value
                                        .multiply(price)
                                        .setScale(selectedToken.decimal, RoundingMode.DOWN)
                                        .stripTrailingZeros()
                                }
                                ?.takeIf { it.isNotEmpty() } ?: return@combine

                        lastTokenValueUserInput = tokenString
                        lastFiatValueUserInput = fiatValue

                        fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                    } else if (lastFiatValueUserInput != fiatString) {
                        val tokenValue =
                            convertValue(fiatString, selectedToken) { value, price, token ->
                                    value.divide(price, token.decimal, RoundingMode.DOWN)
                                }
                                ?.takeIf { it.isNotEmpty() } ?: return@combine

                        val tokenDecimal = tokenValue.toBigDecimalOrNull()
                        isMaxAmount.value = tokenDecimal == maxAmount && maxAmount > BigDecimal.ZERO

                        lastTokenValueUserInput = tokenValue
                        lastFiatValueUserInput = fiatString

                        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                    }
                }
                .collect()
        }
    }

    private fun collectAmountChecks() {
        viewModelScope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    tokenAmountFieldState.textAsFlow(),
                    gasFee.filterNotNull(),
                ) { selectedToken, tokenAmount, gasFee ->
                    val reapingError =
                        chainValidationService.checkIsReapable(
                            selectedAccount,
                            selectedToken,
                            tokenAmount.toString(),
                            gasFee,
                        )
                    uiState.update { it.copy(reapingError = reapingError) }
                }
                .collect()
        }
    }

    private suspend fun convertValue(
        value: String,
        token: Coin?,
        transform: (value: BigDecimal, price: BigDecimal, token: Coin) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull()

        return if (decimalValue != null) {
            val selectedToken = token ?: return null

            val price =
                try {
                    tokenPriceRepository.getPrice(selectedToken, appCurrency.value).first()
                } catch (e: Exception) {
                    Timber.d("Failed to get price for token $selectedToken")
                    return null
                }

            if (price == BigDecimal.ZERO) {
                Timber.w(
                    "convertValue: price is ZERO for token %s, skipping conversion",
                    selectedToken.ticker,
                )
                return null
            }

            transform(decimalValue, price, selectedToken).toPlainString()
        } else {
            ""
        }
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        if (dstAddress.isBlank()) return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    private fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

    fun enableAdvanceGasUi() {
        advanceGasUiRepository.showIcon()
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

    private suspend fun accountValidation(): AccountValidation {
        val vaultId =
            vaultId
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        val selectedAccount =
            selectedAccount
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        val chain = selectedAccount.token.chain

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()
        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val gasFee = awaitGasFee()

        if (!selectedAccount.token.allowZeroGas() && gasFee.value <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_gas_fee)
            )
        }

        val dstAddress =
            try {
                addressParserRepository.resolveName(addressFieldState.text.asAddressInput(), chain)
            } catch (e: Exception) {
                Timber.e(e)
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.failed_to_resolve_address)
                )
            }

        return AccountValidation(
            vaultId = vaultId,
            selectedAccount = selectedAccount,
            chain = chain,
            gasFee = gasFee,
            dstAddress = dstAddress,
        )
    }

    private suspend fun createRUJIUnstakeDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        val depositMemo = "withdraw:${selectedToken.contractAddress}:$tokenAmountInt"

        val specific =
            withContext(Dispatchers.IO) {
                blockChainSpecificRepository.getSpecific(
                    chain,
                    srcAddress,
                    selectedToken,
                    gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                    transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                )
            }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = depositMemo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload =
                ThorchainFunctions.unstakeRUJI(
                    fromAddress = srcAddress,
                    stakingContract = STAKING_RUJI_CONTRACT,
                    amount = tokenAmountInt.toString(),
                ),
        )
    }

    private suspend fun createRUJIRewardsDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        val memo = ThorchainFunctions.rujiRewardsMemo(selectedToken.contractAddress, tokenAmountInt)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = memo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload =
                ThorchainFunctions.claimRujiRewards(
                    fromAddress = srcAddress,
                    stakingContract = STAKING_RUJI_CONTRACT,
                ),
        )
    }

    private suspend fun createYTCUnstakeDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        totalTokenAmount: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        val percentage =
            if (totalTokenAmount > BigInteger.ZERO) {
                (tokenAmountInt.toDouble() / totalTokenAmount.toDouble()) * 100.0
            } else {
                100.0
            }

        val isAutoCompound = uiState.value.isAutocompound
        val unstakeMemo =
            if (isAutoCompound) {
                ""
            } else {
                val basisPoints = (percentage * 100).toInt().coerceIn(0, 10000)
                ThorchainFunctions.tcyUnstakeMemo(basisPoints)
            }

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType =
                    if (isAutoCompound) {
                        TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT
                    } else {
                        TransactionType.TRANSACTION_TYPE_UNSPECIFIED
                    },
            )

        val unstakePayload =
            if (isAutoCompound) {
                ThorchainFunctions.unStakeTcyCompound(
                    units = tokenAmountInt,
                    stakingContract = STAKING_TCY_COMPOUND_CONTRACT,
                    fromAddress = srcAddress,
                )
            } else {
                null
            }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = unstakeMemo,
            srcTokenValue = TokenValue(value = BigInteger.ZERO, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = unstakePayload,
        )
    }

    private suspend fun createRujiStakeDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        val depositMemo = "bond:${selectedToken.contractAddress}:$tokenAmountInt"

        val specific =
            withContext(Dispatchers.IO) {
                blockChainSpecificRepository.getSpecific(
                    chain,
                    srcAddress,
                    selectedToken,
                    gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                    transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                )
            }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = depositMemo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload =
                ThorchainFunctions.stakeRUJI(
                    fromAddress = srcAddress,
                    stakingContract = STAKING_RUJI_CONTRACT,
                    denom = selectedToken.contractAddress,
                    amount = tokenAmountInt,
                ),
        )
    }

    private suspend fun createTCYStakeDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        val isAutoCompound = uiState.value.isAutocompound
        val stakingMemo =
            if (isAutoCompound) {
                ""
            } else {
                "TCY+"
            }

        val specific =
            withContext(Dispatchers.IO) {
                blockChainSpecificRepository.getSpecific(
                    chain,
                    srcAddress,
                    selectedToken,
                    gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                    transactionType =
                        if (isAutoCompound) {
                            TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT
                        } else {
                            TransactionType.TRANSACTION_TYPE_UNSPECIFIED
                        },
                )
            }

        val stakingPayload =
            if (isAutoCompound) {
                ThorchainFunctions.stakeTcyCompound(
                    fromAddress = srcAddress,
                    stakingContract = STAKING_TCY_COMPOUND_CONTRACT,
                    denom = selectedToken.contractAddress,
                    amount = tokenAmountInt,
                )
            } else {
                null
            }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = stakingMemo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = getFeesFiatValue(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = stakingPayload,
        )
    }

    companion object {
        private const val GAS_FEE_TIMEOUT_MS = 5_000L
        private const val REQUEST_ADDRESS_ID = "request_address_id"
        private const val REQUEST_PROVIDER_ADDRESS_ID = "request_provider_address_id"
    }
}

/**
 * Returns the best matching [SendSrc] for [selectedTokenId] and [filterByChain], or the first entry
 * if neither is specified.
 */
internal fun List<Address>.firstSendSrc(selectedTokenId: String?, filterByChain: Chain?): SendSrc {
    val address =
        when {
            !selectedTokenId.isNullOrBlank() ->
                first { it -> it.accounts.any { it.token.id == selectedTokenId } }

            filterByChain != null -> first { it.chain == filterByChain }
            else -> first()
        }

    val account =
        when {
            !selectedTokenId.isNullOrBlank() ->
                address.accounts.first { it.token.id == selectedTokenId }
            filterByChain != null -> address.accounts.first { it.token.isNativeToken }
            else -> address.accounts.first()
        }

    return SendSrc(address, account)
}

/**
 * Returns the [SendSrc] that matches [currentSrc], or delegates to [firstSendSrc] when
 * [selectedTokenId] is non-null.
 */
internal fun List<Address>.findCurrentSrc(selectedTokenId: String?, currentSrc: SendSrc): SendSrc {
    if (selectedTokenId == null) {
        val selectedAddress = currentSrc.address
        val selectedAccount = currentSrc.account
        val address = first {
            it.chain == selectedAddress.chain && it.address == selectedAddress.address
        }
        return SendSrc(
            address,
            address.accounts.first { it.token.ticker == selectedAccount.token.ticker },
        )
    } else {
        return firstSendSrc(selectedTokenId, null)
    }
}

private data class AccountValidation(
    val vaultId: String,
    val selectedAccount: Account,
    val chain: Chain,
    val gasFee: TokenValue,
    val dstAddress: String,
)

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
