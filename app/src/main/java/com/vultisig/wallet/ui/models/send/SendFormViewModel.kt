package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.allowZeroGas
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
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
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

@Immutable
internal data class TokenBalanceUiModel(
    val model: SendSrc,
    val title: String,
    val balance: String?,
    val isNativeToken: Boolean,
    val isLayer2: Boolean,
    val tokenStandard: String?,
    val tokenLogo: ImageModel,
    @DrawableRes val chainLogo: Int,
)

@Immutable
internal data class SendFormUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val from: String = "",
    val fiatCurrency: String = "",
    val gasFee: UiText = UiText.Empty,
    val totalGas: UiText = UiText.Empty,
    val estimatedFee: UiText = UiText.Empty,
    val errorText: UiText? = null,
    val showGasFee: Boolean = true,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val hasMemo: Boolean = false,
    val showGasSettings: Boolean = false,
    val specific: BlockChainSpecificAndUtxo? = null,
    val isLoading: Boolean = false,
)

internal data class SendSrc(
    val address: Address,
    val account: Account,
)

internal data class EthGasSettings(
    val priorityFee: BigInteger,
    val gasLimit: BigInteger,
)

internal data class InvalidTransactionDataException(
    val text: UiText,
) : Exception()


@HiltViewModel
internal class SendFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapGasFeeToString: TokenValueToStringWithUnitMapper,
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
) : ViewModel() {

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

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    private var ethGasSettings = MutableStateFlow<EthGasSettings?>(null)

    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)
    private var maxAmount = BigDecimal.ZERO

    private var lastTokenValueUserInput = ""
    private var lastFiatValueUserInput = ""


    init {
        loadSelectedCurrency()
        collectSelectedAccount()
        collectAmountChanges()
        calculateGasFees()
        calculateSpecific()
        collectAdvanceGasUi()
    }

    fun loadData(
        vaultId: VaultId,
        preSelectedChainId: ChainId?,
        preSelectedTokenId: TokenId?,
    ) {
        memoFieldState.clearText()

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId

            loadAccounts(vaultId)
        }

        preSelectToken(
            preSelectedChainIds = listOf(preSelectedChainId),
            preSelectedTokenId = preSelectedTokenId,
        )
    }

    fun validateDstAddress() = viewModelScope.launch {
        val errorText = validateDstAddress(addressFieldState.text.toString())
        uiState.update {
            it.copy(dstAddressError = errorText)
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        uiState.update { it.copy(tokenAmountError = errorText) }
    }

    fun openTokenSelection() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.navigate(
                Destination.SelectToken(
                    vaultId = vaultId,
                    targetArg = Destination.SelectToken.ARG_SELECTED_TOKEN_ID,
                )
            )

            val tokenSelectionResult = requestResultRepository
                .request<Coin?>(Destination.SelectToken.ARG_SELECTED_TOKEN_ID)

            if (tokenSelectionResult != null) {
                selectToken(tokenSelectionResult)
            }
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

                    preSelectToken(
                        preSelectedChainIds = chainValidForAddress.map { it.id },
                        preSelectedTokenId = null,
                        forcePreselection = true
                    )
                }
            }
        }
    }

    fun setOutputAddress(address: String) {
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scanAddress() {
        viewModelScope.launch {
            navigator.navigate(Destination.ScanQr)
        }
    }

    fun openAddressBook() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.AddressBook(
                    requestId = REQUEST_ADDRESS_ID,
                    chain = selectedTokenValue?.chain,
                )
            )
            val address: AddressBookEntry = requestResultRepository.request(REQUEST_ADDRESS_ID)

            val vaultId = vaultId
            val selectedChain = address.chain
            if (vaultId != null && selectedTokenValue?.chain != selectedChain) {
                preSelectToken(
                    preSelectedChainIds = listOf(selectedChain.id),
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

    fun saveGasSettings(settings: EthGasSettings) {
        ethGasSettings.value = settings
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

        return availableTokenBalance?.copy(
            value = (BigDecimal(availableTokenBalance.value) * percentage.toBigDecimal()).toBigInteger()
        )?.decimal
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

                val selectedToken = selectedAccount.token

                val tokenAmountInt =
                    tokenAmount
                        .movePointRight(selectedToken.decimal)
                        .toBigInteger()

                val srcAddress = selectedToken.address
                val isMaxAmount = tokenAmount == maxAmount

                val specific = blockChainSpecificRepository
                    .getSpecific(
                        chain,
                        srcAddress,
                        selectedToken,
                        gasFee,
                        isSwap = false,
                        isMaxAmountEnabled = isMaxAmount,
                        isDeposit = false,
                        dstAddress = dstAddress
                    )
                    .let {
                        val ethSettings = ethGasSettings.value
                        if (ethSettings != null) {
                            val spec = it.blockChainSpecific
                            if (spec is BlockChainSpecific.Ethereum) {
                                it.copy(
                                    blockChainSpecific = spec
                                        .copy(
                                            priorityFeeWei = ethSettings.priorityFee,
                                            gasLimit = ethSettings.gasLimit,
                                        )
                                )
                            } else it
                        } else {
                            it
                        }
                    }

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
                        gasFee = gasFee,
                        selectedToken = selectedToken,
                    )
                )

                val transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    chainId = chain.raw,
                    tokenId = selectedToken.id,
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
                    memo = memoFieldState.text.toString().takeIf { it.isNotEmpty() },
                    estimatedFee = totalGasAndFee.formattedFiatValue,
                    totalGass = totalGasAndFee.formattedTokenValue,
                )

                Timber.d("Transaction: $transaction")

                transactionRepository.addTransaction(transaction)
                advanceGasUiRepository.hideIcon()
                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                        vaultId = vaultId,
                    )
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } finally {
                hideLoading()
            }
        }
    }

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

        selectedToken.value = token
    }

    private fun calculateGasLimit(
        chain: Chain,
        specific: BlockChainSpecific?,
    ): BigInteger = if (chain.standard == TokenStandard.EVM && specific != null) {
        (specific as BlockChainSpecific.Ethereum).gasLimit
    } else {
        BigInteger.valueOf(1)
    }

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
                    accounts, preSelectedChainIds, preSelectedTokenId
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

    private fun calculateGasFees() {
        viewModelScope.launch {
            selectedToken
                .filterNotNull()
                .map {
                    gasFeeRepository.getGasFee(it.chain, it.address)
                }
                .catch {
                    // TODO handle error when querying gas fee
                    Timber.e(it)
                }
                .collect { gasFee ->
                    this@SendFormViewModel.gasFee.value = gasFee

//                    uiState.update {
//                        it.copy(gasFee = mapGasFeeToString(gasFee))
//                    }
                }
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

                    val estimatedFee = gasFeeToEstimatedFee(
                        GasFeeParams(
                            gasLimit = if (chain.standard == TokenStandard.EVM) {
                                (specific.value?.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                            } else {
                                BigInteger.valueOf(1)
                            },
                            gasFee = gasFee,
                            selectedToken = token,
                        )
                    )

                    uiState.update {
                        it.copy(
                            estimatedFee = UiText.DynamicString(estimatedFee.formattedFiatValue),
                            totalGas = UiText.DynamicString(estimatedFee.formattedTokenValue)
                        )
                    }
                } catch (e: Exception) {
                    // todo handle errors
                    Timber.e(e)
                }
            }.collect()
        }
    }

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
                val showGasFee = !token.allowZeroGas()
                val hasMemo = token.isNativeToken

                val uiModel = accountToTokenBalanceUiModelMapper.map(SendSrc(
                    Address(
                        chain = token.chain,
                        address = address,
                        accounts = accounts,
                    ),
                    accounts.find { it.token.id == token.id } ?: Account(
                        token = token,
                        tokenValue = null,
                        fiatValue = null,
                    )
                ))

                advanceGasUiRepository.updateTokenStandard(token.chain.standard)
                uiState.update {
                    it.copy(
                        from = address,
                        selectedCoin = uiModel,
                        showGasFee = showGasFee,
                        hasMemo = hasMemo,
                    )
                }
            }.collect()
        }
    }

    private fun collectAmountChanges() {
        viewModelScope.launch {
            combine(
                tokenAmountFieldState.textAsFlow(),
                fiatAmountFieldState.textAsFlow(),
            ) { tokenFieldValue, fiatFieldValue ->
                val tokenString = tokenFieldValue.toString()
                val fiatString = fiatFieldValue.toString()
                if (lastTokenValueUserInput != tokenString) {
                    val fiatValue = convertValue(tokenString) { value, price, token ->
                        value.multiply(price)
                    } ?: return@combine

                    lastTokenValueUserInput = tokenString
                    lastFiatValueUserInput = fiatValue

                    fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                } else if (lastFiatValueUserInput != fiatString) {
                    val tokenValue = convertValue(fiatString) { value, price, token ->
                        value.divide(price, token.decimal, RoundingMode.HALF_UP)
                    } ?: return@combine

                    lastTokenValueUserInput = tokenValue
                    lastFiatValueUserInput = fiatString

                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                }
            }.collect()
        }
    }

    private suspend fun convertValue(
        value: String,
        transform: (
            value: BigDecimal,
            price: BigDecimal,
            token: Coin,
        ) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull()

        return if (decimalValue != null) {
            val selectedToken = selectedTokenValue
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

    companion object {
        private const val REQUEST_ADDRESS_ID = "request_address_id"
    }

}

