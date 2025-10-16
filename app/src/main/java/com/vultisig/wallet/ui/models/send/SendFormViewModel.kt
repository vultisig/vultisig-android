@file:kotlin.OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.RippleHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.allowZeroGas
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.hasReaping
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.toValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlin.uuid.Uuid


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

    // errors
    val errorText: UiText? = null,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val reapingError: UiText? = null,

    val hasMemo: Boolean = false,
    val showGasFee: Boolean = true,
    val hasGasSettings: Boolean = false,
    val showGasSettings: Boolean = false,
    val specific: BlockChainSpecificAndUtxo? = null,

    val expandedSection: SendSections = SendSections.Asset,
    val usingTokenAmountInput: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

internal data class SendSrc(
    val address: Address,
    val account: Account,
)

internal enum class SendSections {
    Asset,
    Address,
    Amount,
}

internal sealed class GasSettings {
    data class Eth(
        val baseFee: BigInteger,
        val priorityFee: BigInteger,
        val gasLimit: BigInteger,
    ) : GasSettings()

    data class UTXO(
        val byteFee: BigInteger,
    ) : GasSettings()
}

internal data class InvalidTransactionDataException(
    val text: UiText,
) : Exception()

@HiltViewModel
internal class SendFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val requestQrScan: RequestQrScanUseCase,
    private val accountsRepository: AccountsRepository,
    appCurrencyRepository: AppCurrencyRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val gasFeeRepository: GasFeeRepository,
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val requestResultRepository: RequestResultRepository,
    private val addressParserRepository: AddressParserRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val advanceGasUiRepository: AdvanceGasUiRepository,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val feeServiceComposite: FeeServiceComposite,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Send>()

    val uiState = MutableStateFlow(SendFormUiModel())

    val addressFieldState = TextFieldState()
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val memoFieldState = TextFieldState()

    private var vaultId: String? = null

    private val selectedToken = MutableStateFlow<Coin?>(null)

    private val selectedTokenValue: Coin?
        get() = selectedToken.value

    private val accounts = MutableStateFlow(emptyList<Account>())

    private val selectedAccount: Account?
        get() {
            val selectedTokenValue = selectedTokenValue
            val accounts = accounts.value
            return accounts.find { it.token.id == selectedTokenValue?.id }
        }

    private var preSelectTokenJob: Job? = null
    private var loadAccountsJob: Job? = null

    private val appCurrency = appCurrencyRepository
        .currency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    private val planFee = MutableStateFlow<Long?>(null)
    private val planUtxo = MutableStateFlow<List<String>?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    private var gasSettings = MutableStateFlow<GasSettings?>(null)

    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)
    private var maxAmount = BigDecimal.ZERO

    private var lastTokenValueUserInput = ""
    private var lastFiatValueUserInput = ""


    init {
        loadData(
            vaultId = args.vaultId,
            preSelectedChainId = args.chainId,
            preSelectedTokenId = args.tokenId,
            address = args.address,
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
    }

    private fun collectAddress() {
        viewModelScope.launch {
            addressFieldState.textAsFlow()
                .combine(selectedToken.filterNotNull()) { address, token ->
                    val isAddressValid =
                        chainAccountAddressRepository.isValid(token.chain, address.toString())
                    if (isAddressValid) {
                        expandSection(SendSections.Amount)
                    }
                }.collect()
        }
    }

    private fun loadGasSettings() {
        viewModelScope.launch {
            advanceGasUiRepository.shouldShowAdvanceGasSettingsIcon
                .collect { shouldShowAdvanceGasSettingsIcon ->
                    uiState.update { it.copy(hasGasSettings = shouldShowAdvanceGasSettingsIcon) }
                }
        }
    }

    fun loadData(
        vaultId: VaultId,
        preSelectedChainId: ChainId?,
        preSelectedTokenId: TokenId?,
        address: String?,
    ) {
        memoFieldState.clearText()

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId

            loadAccounts(vaultId)
            loadVaultName()
        }

        if (address != null) {
            setAddressFromQrCode(address)
        } else {
            preSelectToken(
                preSelectedChainIds = listOf(preSelectedChainId),
                preSelectedTokenId = preSelectedTokenId,
            )
        }

        if (preSelectedTokenId != null) {
            expandSection(SendSections.Address)
        }
    }

    private fun loadVaultName() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            vaultRepository.get(vaultId)?.let { vault ->
                uiState.update { it.copy(srcVaultName = vault.name) }
            }
        }
    }

    private fun collectDstAddress() {
        viewModelScope.launch {
            addressFieldState.textAsFlow()
                .map { it.toString() }
                .collect { dstAddress ->
                    val isDstAddressComplete = dstAddress.isNotBlank()
                    uiState.update {
                        it.copy(
                            isDstAddressComplete = isDstAddressComplete,
                        )
                    }
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

            val chain: Chain? = requestResultRepository.request(requestId)

            if (chain == null || chain == selectedChain) {
                return@launch
            }

            val account = accounts.value.find {
                it.token.isNativeToken && it.token.chain == chain
            } ?: return@launch

            selectToken(account.token)
        }
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

    fun openGasSettings() {
        viewModelScope.launch {
            advanceGasUiRepository.showSettings()
        }
    }

    fun setAddressFromQrCode(qrCode: String?) {
        if (qrCode != null) {
            Timber.d("setAddressFromQrCode(address = $qrCode)")

            addressFieldState.setTextAndPlaceCursorAtEnd(qrCode)

            val vaultId = vaultId
            if (vaultId != null) {
                val chainValidForAddress = Chain.entries.filter { chain ->
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
                        vaultId = vaultId
                    )

                    preSelectToken(
                        preSelectedChainIds = preSelectedChainIds,
                        preSelectedTokenId = null,
                        forcePreselection = true,
                    )
                }
            }
        }
    }

    private fun checkChainIdExistInAccounts(preSelectedChainIds: List<String>, vaultId: String) {
        // if chain Id is missing in accounts, add the first chain found by address manually.
        val chainIdForAddition = preSelectedChainIds.firstOrNull()
        val chainIdNotInAccounts = accounts.value.none {
            it.token.chain.id.equals(chainIdForAddition, ignoreCase = true)
        }
        if (chainIdForAddition != null && chainIdNotInAccounts) {
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
        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
            coin = nativeToken,
            vault = vault
        )
        val updatedCoin = nativeToken.copy(
            address = address,
            hexPublicKey = derivedPublicKey
        )

        vaultRepository.addTokenToVault(vaultId, updatedCoin)
    }

    fun setOutputAddress(address: String) {
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scanAddress() {
        viewModelScope.launch {
            val qr = requestQrScan.invoke()
            if (qr != null) {
                setAddressFromQrCode(qr)
            }
        }
    }

    fun openAddressBook() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            navigator.route(
                Route.AddressBook(
                    requestId = REQUEST_ADDRESS_ID,
                    chainId = selectedChain.id,
                    excludeVaultId = vaultId,
                )
            )

            val address: AddressBookEntry = requestResultRepository.request(REQUEST_ADDRESS_ID)
                ?: return@launch

            val selectedNewChain = address.chain
            if (selectedChain != selectedNewChain) {
                preSelectToken(
                    preSelectedChainIds = listOf(selectedNewChain.id),
                    preSelectedTokenId = null,
                    forcePreselection = true
                )
            }

            setOutputAddress(address.address)
        }
    }

    fun dismissGasSettings() {
        advanceGasUiRepository.hideSettings()
    }

    fun saveGasSettings(settings: GasSettings) {
        gasSettings.value = settings
    }

    fun chooseMaxTokenAmount() {
        viewModelScope.launch {
            val max = fetchPercentageOfAvailableBalance(1f)

            maxAmount = max
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(
                max?.toPlainString() ?: ""
            )
        }
    }

    fun choosePercentageAmount(percentage: Float) {
        viewModelScope.launch {
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(
                fetchPercentageOfAvailableBalance(percentage)?.toPlainString() ?: ""
            )
        }
    }

    private suspend fun fetchPercentageOfAvailableBalance(percentage: Float): BigDecimal? {
        val selectedAccount = selectedAccount ?: return null
        val gasFee = gasFee.value ?: return null
        val chain = selectedAccount.token.chain

        val specific = specific.value?.blockChainSpecific

        val gasLimit = calculateGasLimit(chain, specific)
        val availableTokenBalance = getAvailableTokenBalance(
            selectedAccount,
            gasFee.value.multiply(gasLimit)
        )

        return availableTokenBalance?.decimal
            ?.multiply(percentage.toBigDecimal())
            ?.setScale(
                selectedAccount.token.decimal,
                RoundingMode.DOWN
            )
            ?.stripTrailingZeros()
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun send() {
        viewModelScope.launch {
            showLoading()
            try {
                val vaultId = vaultId
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val selectedAccount = selectedAccount
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val chain = selectedAccount.token.chain

                val gasFee = gasFee.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )

                if (!selectedAccount.token.allowZeroGas() && gasFee.value <= BigInteger.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )
                }
                val dstAddress = try {
                    addressParserRepository.resolveName(
                        addressFieldState.text.toString(),
                        chain,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.failed_to_resolve_address)
                    )
                }

                if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text
                    .toString()
                    .toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val selectedTokenValue = selectedAccount.tokenValue
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val memo = memoFieldState.text.toString().takeIf { it.isNotEmpty() }

                val selectedToken = selectedAccount.token

                val tokenAmountInt =
                    tokenAmount
                        .movePointRight(selectedToken.decimal)
                        .toBigInteger()

                val srcAddress = selectedToken.address
                val isMaxAmount = tokenAmount == maxAmount

                if (chain == Chain.Tron) {
                    if (srcAddress == dstAddress) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_same_address)
                        )
                    }
                }

                val specific = blockChainSpecificRepository
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
                        dstAddress = dstAddress
                    )
                    .let {
                        val gasSettings = gasSettings.value
                        if (gasSettings != null) {
                            val spec = it.blockChainSpecific

                            when {
                                gasSettings is GasSettings.Eth && spec is BlockChainSpecific.Ethereum -> {
                                    it.copy(
                                        blockChainSpecific = spec
                                            .copy(
                                                maxFeePerGasWei = gasSettings.baseFee,
                                                priorityFeeWei = gasSettings.priorityFee,
                                                gasLimit = gasSettings.gasLimit,
                                            )
                                    )
                                }

                                gasSettings is GasSettings.UTXO && spec is BlockChainSpecific.UTXO -> {
                                    it.copy(
                                        blockChainSpecific = spec
                                            .copy(
                                                byteFee = gasSettings.byteFee,
                                            )
                                    )
                                }

                                else -> it
                            }
                        } else {
                            it
                        }
                    }.let { selectUtxosIfNeeded(chain, tokenAmountInt, it) }


                if (selectedToken.isNativeToken) {
                    val availableTokenBalance = getAvailableTokenBalance(
                        selectedAccount,
                        gasFee.value.multiply(
                            calculateGasLimit(chain, specific.blockChainSpecific)
                        )
                    )?.value ?: BigInteger.ZERO

                    if (tokenAmountInt > availableTokenBalance) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    }

                    if (chain == Chain.Cardano) {
                        validateCardanoUTXORequirements(
                            sendAmount = tokenAmountInt,
                            totalBalance = selectedTokenValue.value,
                            estimatedFee = gasFee.value
                        )
                    }

                    if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                        validateBtcLikeAmount(tokenAmountInt, chain)
                    }
                } else {
                    val nativeTokenAccount = accounts.value
                        .find { it.token.isNativeToken && it.token.chain == chain }
                    val nativeTokenValue = nativeTokenAccount?.tokenValue?.value
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                    if (selectedTokenValue.value < tokenAmountInt
                    ) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    } else if (nativeTokenValue < gasFee.value
                    ) {
                        throw InvalidTransactionDataException(
                            UiText.FormattedText(
                                R.string.insufficient_native_token,
                                listOf(nativeTokenAccount.token.ticker)
                            )
                        )
                    }
                }

                val totalGasAndFee = gasFeeToEstimatedFee(
                    GasFeeParams(
                        gasLimit = if (chain.standard == TokenStandard.EVM) {
                            (specific.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                        } else {
                            BigInteger.valueOf(1)
                        },
                        gasFee = if (chain.standard == TokenStandard.UTXO) {
                            val plan = planFee.value ?: throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.send_error_invalid_plan_fee)
                            )
                            if (plan > 0) gasFee.copy(
                                value = BigInteger.valueOf(plan)
                            )
                            else gasFee
                        } else gasFee,
                        selectedToken = selectedToken,
                    )
                )

                val transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    chainId = chain.raw,
                    token = selectedToken,
                    srcAddress = srcAddress,
                    dstAddress = dstAddress,
                    tokenValue = TokenValue(
                        value = tokenAmountInt,
                        unit = selectedTokenValue.unit,
                        decimals = selectedToken.decimal,
                    ),
                    fiatValue = FiatValue(
                        value = fiatAmountFieldState.text.toString().toBigDecimalOrNull()
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
                advanceGasUiRepository.hideIcon()

                navigator.route(
                    Route.VerifySend(
                        transactionId = transaction.id,
                        vaultId = vaultId,
                    )
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

    private fun validateBtcLikeAmount(tokenAmountInt: BigInteger, chain: Chain) {
        val minAmount = chain.getDustThreshold
        if (tokenAmountInt < minAmount) {
            val symbol = chain.coinType.symbol
            val name = chain.raw
            val formattedMinAmount = chain.toValue(minAmount).toString()
            throw InvalidTransactionDataException(
                UiText.DynamicString("Minimum send amount is $formattedMinAmount $symbol. $name requires this to prevent spam.")
            )
        }
    }

    private suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan {
        val vault = vaultRepository.get(vaultId) ?: error("Can't calculate plan fees")

        val keysignPayload = KeysignPayload(
            coin = selectedToken,
            toAddress = dstAddress,
            toAmount = tokenAmountInt,
            blockChainSpecific = specific.blockChainSpecific,
            memo = memo,
            vaultPublicKeyECDSA = vault.pubKeyECDSA,
            vaultLocalPartyID = vault.localPartyID,
            utxos = specific.utxos,
            libType = vault.libType,
            wasmExecuteContractPayload = null,
        )

        val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)

        val plan = utxo.getBitcoinTransactionPlan(keysignPayload)
        return plan
    }

    private fun selectUtxosIfNeeded(
        chain: Chain,
        tokenAmount: BigInteger,
        specific: BlockChainSpecificAndUtxo
    ): BlockChainSpecificAndUtxo {
        val spec = specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        return specific
    }
    /*private fun selectUtxosIfNeeded(
        chain: Chain,
        tokenAmount: BigInteger,
        specific: BlockChainSpecificAndUtxo
    ): BlockChainSpecificAndUtxo {
        val spec = specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        val totalAmount = tokenAmount + spec.byteFee * 1480.toBigInteger()
        val resultingUtxos = mutableListOf<UtxoInfo>()
        val existingUtxos = specific.utxos
        var total = 0L
        for (utxo in existingUtxos) {
            if (utxo.amount < chain.getDustThreshold.toLong()) {
                continue
            }
            resultingUtxos.add(utxo)
            total += utxo.amount
            if (total >= totalAmount.toLong()) {
                break
            }
        }

        return specific.copy(utxos = resultingUtxos)
    } */

    private fun hideLoading() {
        uiState.update {
            it.copy(
                isLoading = false
            )
        }
    }

    private fun showLoading() {
        uiState.update {
            it.copy(isLoading = true)
        }
    }

    private fun selectToken(token: Coin) {
        Timber.d("selectToken(token = $token)")

        lastTokenValueUserInput = ""
        selectedToken.value = token
    }

    private fun calculateGasLimit(
        chain: Chain,
        specific: BlockChainSpecific?,
    ): BigInteger =
        (specific as? BlockChainSpecific.Ethereum)?.gasLimit ?: BigInteger.valueOf(1)

    private fun showError(text: UiText) {
        uiState.update { it.copy(errorText = text) }
    }

    private fun loadAccounts(vaultId: VaultId) {
        loadAccountsJob?.cancel()
        loadAccountsJob = viewModelScope.launch {
            accountsRepository.loadAddresses(vaultId)
                .map { addrs -> addrs.flatMap { it.accounts } }
                .collect(accounts)
        }
    }

    private fun preSelectToken(
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
        forcePreselection: Boolean = false,
    ) {
        Timber.d("preSelectToken($preSelectedChainIds, $preSelectedTokenId, $forcePreselection)")

        preSelectTokenJob?.cancel()
        preSelectTokenJob = viewModelScope.launch {
            accounts.collect { accounts ->
                val preSelectedToken = findPreselectedToken(
                    accounts, preSelectedChainIds, preSelectedTokenId,
                )

                Timber.d("Found a new token to pre select $preSelectedToken")

                // if user hasn't yet selected any token, preselect found token
                if ((forcePreselection || selectedTokenValue == null) && preSelectedToken != null) {
                    selectToken(preSelectedToken)
                }
            }
        }
    }

    /**
     * Returns first token found for tokenId or chainId or first token it all list,
     * can return null if there's no tokens in the vault
     */
    private fun findPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        var searchByChainResult: Coin? = null

        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id == preSelectedTokenId) {
                // if we find token by id, return it asap
                return accountToken
            }
            if (searchByChainResult == null && preSelectedChainIds.contains(accountToken.chain.id)) {
                // if we find token by chain, remember it and return later if nothing else found
                searchByChainResult = accountToken
            }
        }
        // if user selected none, or nothing was found, select the first token
        return searchByChainResult
            ?: accounts.firstOrNull()?.token
    }

    private fun calculateGasTokenBalance() {
        viewModelScope.launch {
            selectedToken
                .filterNotNull()
                .map {
                    if (it.isNativeToken) {
                        null
                    } else {
                        accounts.value.find { account ->
                            account.token.isNativeToken &&
                                    account.token.chain == it.chain
                        }?.tokenValue
                    }
                }
                .collect { gasTokenBalance ->
                    if (gasTokenBalance == null) {
                        uiState.update {
                            it.copy(gasTokenBalance = null)
                        }
                    } else {
                        uiState.update {
                            it.copy(
                                gasTokenBalance = UiText.DynamicString(
                                    mapTokenValueToString(
                                        gasTokenBalance
                                    )
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun calculateGasFees() {
        viewModelScope.launch {
            combine(
                selectedToken
                    .filterNotNull()
                    .combine(addressFieldState.textAsFlow()) { token, dst -> token to dst.toString() }
                    .combine(memoFieldState.textAsFlow()) { (token, dst), memo ->
                        Triple(token, dst, memo.toString())
                    }
                    .debounce(350)
                    .map { (token, dst, memo) ->
                        gasFeeRepository.getGasFee(
                            chain = token.chain,
                            address = token.address,
                            isNativeToken = token.isNativeToken,
                            to = dst,
                            memo = memo

                        )
                    }
                    .catch {
                        Timber.e(it)
                    },
                gasSettings,
                specific,
            )
            { gasFee, gasSettings, specific ->
                this@SendFormViewModel.gasFee.value = adjustGasFee(gasFee, gasSettings, specific)
            }.collect()
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
                try {
                    val chain = token.chain
                    if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano)
                        planFee.value = 1

                    val vaultId = vaultId
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                    val resolvedDstAddress = addressParserRepository.resolveName(
                        dstAddress.toString(),
                        chain,
                    )
                    val tokenAmountInt =
                        tokenAmount.toString().toBigDecimal()
                            .movePointRight(token.decimal)
                            .toBigInteger()

                    val plan = getBitcoinTransactionPlan(
                        vaultId,
                        token,
                        resolvedDstAddress,
                        tokenAmountInt,
                        specific,
                        memo.toString(),
                    )
                    planFee.value = plan.fee
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }.collect()
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
                val estimatedFee = gasFeeToEstimatedFee(
                    GasFeeParams(
                        gasLimit = if (chain.standard == TokenStandard.EVM) {
                            if (gasSettings is GasSettings.Eth)
                                gasSettings.gasLimit
                            else
                                (specific.value?.blockChainSpecific
                                        as? BlockChainSpecific.Ethereum)
                                    ?.gasLimit
                                    ?: BigInteger.valueOf(1)
                        } else {
                            BigInteger.valueOf(1)
                        },
                        gasFee = if (chain.standard == TokenStandard.UTXO) {
                            gasFee.copy(
                                value = BigInteger.valueOf(planFee)
                            )
                        } else gasFee,
                        selectedToken = token,
                        perUnit = true,
                    )
                )

                uiState.update {
                    it.copy(
                        estimatedFee = UiText.DynamicString(estimatedFee.formattedFiatValue),
                        totalGas = UiText.DynamicString(estimatedFee.formattedTokenValue)
                    )
                }
            }.collect()
        }
    }

    private fun calculateSpecific() {
        viewModelScope.launch {
            combine(
                selectedToken.filterNotNull(),
                gasFee.filterNotNull(),
            ) { token, gasFee ->
                val chain = token.chain
                val srcAddress = token.address
                advanceGasUiRepository.updateTokenStandard(
                    token.chain.standard
                )
                try {
                    val spec = blockChainSpecificRepository.getSpecific(
                        chain,
                        srcAddress,
                        token,
                        gasFee,
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                    )
                    specific.value = spec
                    advanceGasUiRepository.updateBlockChainSpecific(
                        spec.blockChainSpecific,
                    )
                    uiState.update {
                        it.copy(
                            specific = spec
                        )
                    }

                } catch (e: Exception) {
                    // todo handle errors
                    Timber.e(e)
                }
            }.collect()
        }
    }

    private fun adjustGasFee(
        gasFee: TokenValue,
        gasSettings: GasSettings?,
        spec: BlockChainSpecificAndUtxo?,
    ) = gasFee.copy(
        value = if (gasSettings is GasSettings.UTXO && spec?.blockChainSpecific is BlockChainSpecific.UTXO) {
            gasSettings.byteFee
        } else
            gasFee.value
    )

    private fun loadSelectedCurrency() {
        viewModelScope.launch {
            appCurrency.collect { appCurrency ->
                uiState.update {
                    it.copy(fiatCurrency = appCurrency.ticker)
                }
            }
        }
    }

    private fun collectAdvanceGasUi() {
        advanceGasUiRepository.showSettings.onEach { showGasSettings ->
            uiState.update {
                it.copy(showGasSettings = showGasSettings)
            }
        }.launchIn(viewModelScope)
    }

    private fun collectSelectedAccount() {
        viewModelScope.launch {
            combine(
                selectedToken.filterNotNull(),
                accounts,
            ) { token, accounts ->
                val address = token.address
                val hasMemo = token.isNativeToken || token.chain.standard == TokenStandard.COSMOS

                val uiModel = accountToTokenBalanceUiModelMapper(
                    SendSrc(
                        Address(
                            chain = token.chain,
                            address = address,
                            accounts = accounts,
                        ),
                        accounts.find { it.token.id == token.id } ?: Account(
                            token = token,
                            tokenValue = null,
                            fiatValue = null,
                            price = null,
                        )
                    )
                )

                advanceGasUiRepository.updateTokenStandard(token.chain.standard)
                uiState.update {
                    it.copy(
                        srcAddress = address,
                        selectedCoin = uiModel,
                        hasMemo = hasMemo
                    )
                }
            }.collect()
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
                    val fiatValue =
                        convertValue(tokenString, selectedToken) { value, price, token ->
                            // this is the fiat value , we should not keep too much decimal places
                            value.multiply(price).setScale(3, RoundingMode.HALF_UP)
                                .stripTrailingZeros()
                        } ?: return@combine

                    lastTokenValueUserInput = tokenString
                    lastFiatValueUserInput = fiatValue

                    fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                } else if (lastFiatValueUserInput != fiatString) {
                    val tokenValue =
                        convertValue(fiatString, selectedToken) { value, price, token ->
                            value.divide(price, token.decimal, RoundingMode.HALF_UP)
                        } ?: return@combine

                    lastTokenValueUserInput = tokenValue
                    lastFiatValueUserInput = fiatString

                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                }
            }.collect()
        }
    }

    private fun collectAmountChecks() {
        viewModelScope.launch {
            combine(
                selectedToken.filterNotNull(),
                tokenAmountFieldState.textAsFlow(),
                gasFee.filterNotNull(),
            ) { selectedToken, tokenAmount, gasFee ->
                checkIsReapable(selectedToken, tokenAmount.toString(), gasFee)
            }.collect()
        }
    }

    private fun checkIsReapable(selectedToken: Coin, tokenAmount: String, gasFee: TokenValue) {
        val selectedAccount = selectedAccount
        if (selectedAccount != null) {
            val selectedChain = selectedToken.chain

            if (selectedChain.hasReaping) {
                val balance = selectedAccount.tokenValue
                    ?.value
                    ?: BigInteger.ZERO
                val tokenAmountInt = tokenAmount
                    .toBigDecimalOrNull()
                    ?.movePointRight(selectedToken.decimal)
                    ?.toBigInteger()
                    ?: BigInteger.ZERO

                val existentialDeposit = when {
                    selectedChain == Chain.Polkadot &&
                            selectedToken.ticker == Coins.Polkadot.DOT.ticker -> {
                        PolkadotHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()
                    }

                    selectedChain == Chain.Ripple &&
                            selectedToken.ticker == Coins.Ripple.XRP.ticker -> {
                        RippleHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()
                    }

                    else -> return
                }

                if (balance - (gasFee.value + tokenAmountInt) < existentialDeposit) {
                    uiState.update {
                        it.copy(
                            reapingError = UiText.StringResource(
                                when (selectedChain) {
                                    Chain.Polkadot -> R.string.send_form_polka_reaping_warning
                                    Chain.Ripple -> R.string.send_form_ripple_reaping_warning
                                    else -> return
                                }
                            )
                        )
                    }
                } else {
                    uiState.update {
                        it.copy(reapingError = null)
                    }
                }
            }


        }
    }

    private suspend fun convertValue(
        value: String,
        token: Coin?,
        transform: (
            value: BigDecimal,
            price: BigDecimal,
            token: Coin,
        ) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull()

        return if (decimalValue != null) {
            val selectedToken = token
                ?: return null

            val price = try {
                tokenPriceRepository.getPrice(
                    selectedToken,
                    appCurrency.value,
                ).first()
            } catch (e: Exception) {
                Timber.d("Failed to get price for token $selectedToken")
                return null
            }

            if (price == BigDecimal.ZERO)
                return null

            transform(decimalValue, price, selectedToken)
                .toPlainString()
        } else {
            ""
        }
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        if (dstAddress.isBlank())
            return UiText.StringResource(R.string.send_error_no_address)
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
        viewModelScope.launch {
            navigator.back()
        }
    }

    fun toggleAmountInputType(usingTokenAmountInput: Boolean) {
        uiState.update {
            it.copy(
                usingTokenAmountInput = usingTokenAmountInput
            )
        }
    }

    fun expandSection(section: SendSections) {
        uiState.update {
            it.copy(
                expandedSection = section
            )
        }
    }

    fun refreshGasFee() {
        val srcAddress = selectedToken.value ?: return
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    isRefreshing = true
                )
            }
            val gasFee = try {
                gasFeeRepository.getGasFee(
                    chain = srcAddress.chain,
                    address = srcAddress.address,
                    isNativeToken = srcAddress.isNativeToken,
                    to = addressFieldState.text.toString(),
                    memo = memoFieldState.text.toString(),
                )
            } catch (e: Exception) {
                uiState.update {
                    it.copy(
                        isRefreshing = false
                    )
                }
                return@launch
            }

            this@SendFormViewModel.gasFee.value =
                adjustGasFee(gasFee, gasSettings.value, specific.value)


            // Rapid toggling of isRefreshing can cause the initial true value to be skipped,
            // displaying only the false value in the UI resulting in the swipe refresh being frozen.
            // this line prevent missing true value in these cases.
            delay(100)

            uiState.update {
                it.copy(
                    isRefreshing = false
                )
            }
        }
    }

    private fun validateCardanoUTXORequirements(
        sendAmount: BigInteger,
        totalBalance: BigInteger,
        estimatedFee: BigInteger,
    ) {
        val minUTXOValue: BigInteger = Chain.Cardano.getDustThreshold

        // 1. Check send amount meets minimum
        if (sendAmount < minUTXOValue) {
            val minAmountADA = Chain.Cardano.toValue(minUTXOValue)
            throw InvalidTransactionDataException(
                UiText.DynamicString("Minimum send amount is $minAmountADA ADA. Cardano requires this to prevent spam.")
            )
        }

        // 2. Check sufficient balance
        val totalNeeded = sendAmount + estimatedFee
        if (totalBalance < totalNeeded) {
            val totalBalanceADA = Chain.Cardano.toValue(totalBalance)
            val errorMessage = if (totalBalance > estimatedFee && totalBalance > BigInteger.ZERO) {
                "Insufficient balance.  Try 'Send Max' to send $totalBalanceADA ADA instead."
            } else {
                "Insufficient balance ($totalBalanceADA ADA). You need more ADA to complete this transaction."
            }
            throw InvalidTransactionDataException(UiText.DynamicString(errorMessage))
        }

        // 3. Check remaining balance (change) meets minimum UTXO requirement
        val remainingBalance = totalBalance - sendAmount - estimatedFee
        if (remainingBalance > BigInteger.ZERO && remainingBalance < minUTXOValue) {
            val totalBalanceADA = Chain.Cardano.toValue(totalBalance)

            throw InvalidTransactionDataException(
                UiText.DynamicString(
                    "This amount would leave too little change. 💡 Try 'Send Max' ($totalBalanceADA ADA) to avoid this issue."
                )
            )
        }
    }

    companion object {
        private const val REQUEST_ADDRESS_ID = "request_address_id"
    }
}

internal fun List<Address>.firstSendSrc(
    selectedTokenId: String?,
    filterByChain: Chain?,
): SendSrc {
    val address = when {
        selectedTokenId != null -> first { it -> it.accounts.any { it.token.id == selectedTokenId } }
        filterByChain != null -> first { it.chain == filterByChain }
        else -> first()
    }

    val account = when {
        selectedTokenId != null -> address.accounts.first { it.token.id == selectedTokenId }
        filterByChain != null -> address.accounts.first { it.token.isNativeToken }
        else -> address.accounts.first()
    }

    return SendSrc(address, account)
}

internal fun List<Address>.findCurrentSrc(
    selectedTokenId: String?,
    currentSrc: SendSrc,
): SendSrc {
    if (selectedTokenId == null) {
        val selectedAddress = currentSrc.address
        val selectedAccount = currentSrc.account
        val address = first {
            it.chain == selectedAddress.chain &&
                    it.address == selectedAddress.address
        }
        return SendSrc(
            address,
            address.accounts.first {
                it.token.ticker == selectedAccount.token.ticker
            },
        )
    } else {
        return firstSendSrc(selectedTokenId, null)
    }
}

