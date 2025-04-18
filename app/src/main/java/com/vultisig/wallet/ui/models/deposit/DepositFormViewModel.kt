package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositMemo.Bond
import com.vultisig.wallet.data.models.DepositMemo.Unbond
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import vultisig.keysign.v1.TransactionType
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

internal enum class DepositOption {
    Bond,
    Unbond,
    Leave,
    Stake,
    Unstake,
    Custom,
    TransferIbc,
    Switch,
    Merge,
}

@Immutable
internal data class DepositFormUiModel(
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
    val isLoading: Boolean = false,
    val balance :UiText= UiText.Empty,

    val selectedDstChain: Chain = Chain.ThorChain,
    val dstChainList: List<Chain> = emptyList(),
    val dstAddressError: UiText? = null,
    val amountError: UiText? = null,
    val memoError: UiText? = null,
    val thorAddressError: UiText? = null,

    val selectedCoin: TokenMergeInfo = tokensToMerge.first(),
    val coinList: List<TokenMergeInfo> = tokensToMerge,
)

@HiltViewModel
internal class DepositFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val gasFeeRepository: GasFeeRepository,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
) : ViewModel() {

    private lateinit var vaultId: String
    private var chain: Chain? = null

    val tokenAmountFieldState = TextFieldState()
    val nodeAddressFieldState = TextFieldState()
    val providerFieldState = TextFieldState()
    val operatorFeeFieldState = TextFieldState()
    val customMemoFieldState = TextFieldState()
    val basisPointsFieldState = TextFieldState()
    val lpUnitsFieldState = TextFieldState()
    val assetsFieldState = TextFieldState()
    val thorAddressFieldState = TextFieldState()

    val state = MutableStateFlow(DepositFormUiModel())
    var isLoading: Boolean
        get() = state.value.isLoading
        set(value) {
            state.update {
                it.copy(isLoading = value)
            }
        }
    fun loadData(
        vaultId: String,
        chainId: String,
    ) {
        this.vaultId = vaultId
        val chain = chainId.let(Chain::fromRaw)
        this.chain = chain

        val depositOptions = when (chain) {
            Chain.ThorChain -> listOf(
                DepositOption.Bond,
                DepositOption.Unbond,
                DepositOption.Leave,
                DepositOption.Custom,
                DepositOption.Merge
            )

            Chain.MayaChain -> listOf(
                DepositOption.Bond,
                DepositOption.Unbond,
                DepositOption.Leave,
                DepositOption.Custom,
            )

            Chain.Kujira -> listOf(
                DepositOption.TransferIbc,
            )

            Chain.GaiaChain -> listOf(
                DepositOption.TransferIbc,
                DepositOption.Switch,
            )

            else -> listOf(
                DepositOption.Stake,
                DepositOption.Unstake,
            )
        }
        val depositOption = depositOptions.first()
        state.update {
            it.copy(
                depositMessage = R.string.deposit_message_deposit_title.asUiText(chain.raw),
                depositOptions = depositOptions,
                depositOption = depositOption,
                depositChain = chain
            )
        }

        val dstChainList = listOf(
            Chain.GaiaChain, Chain.Kujira, Chain.Osmosis,
            Chain.Noble, Chain.Akash, Chain.Dydx
        ).filter { it != chain }

        state.update {
            it.copy(
                selectedDstChain = dstChainList.first(),
                dstChainList = dstChainList,
            )
        }

        viewModelScope.launch {
            try {
                accountsRepository.loadAddress(vaultId, chain)
                    .collect { addresses ->
                        addresses.accounts
                            .firstOrNull { it.token.isNativeToken }
                            ?.tokenValue
                            ?.let(mapTokenValueToStringWithUnit)
                            ?.let { tokenValue ->
                                state.update {
                                    it.copy(balance = tokenValue.asUiText())
                                }
                            }
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun selectDepositOption(option: DepositOption) = viewModelScope.launch {
        resetTextFields()
        state.update {
            it.copy(depositOption = option)
        }
    }

    fun selectDstChain(chain: Chain) {
        state.update {
            it.copy(selectedDstChain = chain)
        }
    }

    private fun resetTextFields() {
        tokenAmountFieldState.clearText()
        nodeAddressFieldState.clearText()
        providerFieldState.clearText()
        operatorFeeFieldState.clearText()
        customMemoFieldState.clearText()
        basisPointsFieldState.clearText()
        lpUnitsFieldState.clearText()
        assetsFieldState.clearText()
    }

    fun validateNodeAddress() {
        val errorText = validateDstAddress(nodeAddressFieldState.text.toString())
        state.update {
            it.copy(nodeAddressError = errorText)
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        state.update { it.copy(tokenAmountError = errorText) }
    }

    fun validateProvider() {
        val errorText = validateDstAddress(providerFieldState.text.toString())
        state.update {
            it.copy(providerError = errorText)
        }
    }

    fun validateOperatorFee() {
        val text = operatorFeeFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            state.update {
                it.copy(operatorFeeError = errorText)
            }
        }
    }

    fun validateCustomMemo() {
        val errorText = validateCustomMemo(customMemoFieldState.text.toString())
        state.update {
            it.copy(customMemoError = errorText)
        }
    }

    fun validateBasisPoints() {
        val text = basisPointsFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            state.update {
                it.copy(basisPointsError = errorText)
            }
        }
    }

    fun setProvider(provider: String) {
        providerFieldState.setTextAndPlaceCursorAtEnd(provider)
    }

    fun setNodeAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scan() {
        viewModelScope.launch {
            navigator.navigate(Destination.ScanQr)
        }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun deposit() {
        viewModelScope.launch {
            try {
                isLoading = true
                val depositOption = state.value.depositOption

                val transaction = when (depositOption) {
                    DepositOption.Bond -> createBondTransaction()
                    DepositOption.Unbond -> createUnbondTransaction()
                    DepositOption.Leave -> createLeaveTransaction()
                    DepositOption.Custom -> createCustomTransaction()
                    DepositOption.Stake -> createStakeTransaction()
                    DepositOption.Unstake -> createUnstakeTransaction()
                    DepositOption.TransferIbc -> createTransferIbcTx()
                    DepositOption.Switch -> createSwitchTx()
                    DepositOption.Merge -> createMergeTx()
                }

                Timber.d("Transaction: $transaction")

                transactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                        vaultId = vaultId,
                    )
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                showError(UiText.StringResource(R.string.dialog_default_error_body))
                Timber.e(e)
            }
            finally {
                isLoading = false
            }
        }
    }

    private suspend fun createBondTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (depositChain == Chain.ThorChain && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val operatorFeeAmount = operatorFeeFieldState.text
            .toString()
            .toBigDecimalOrNull()

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val tokenAmountInt =
            tokenAmount
                ?.movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val operatorFeeValue = operatorFeeAmount
            ?.movePointRight(if (depositChain == Chain.ThorChain) 2 else 0)
            ?.toInt()

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo = when (depositChain) {
            Chain.MayaChain -> Bond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                lpUnits = lpUnits.toIntOrNull(),
                assets = assets
            )

            Chain.ThorChain -> Bond.Thor(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                operatorFee = operatorFeeValue,
            )

            else -> error("chain is invalid")
        }

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createUnbondTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (depositChain == Chain.ThorChain && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val tokenAmountInt =
            tokenAmount
                ?.movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo = when (state.value.depositChain) {
            Chain.MayaChain -> Unbond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                assets = assets,
                lpUnits = lpUnits.toIntOrNull(),
            )

            Chain.ThorChain -> Unbond.Thor(
                nodeAddress = nodeAddress,
                srcTokenValue = TokenValue(
                    value = tokenAmountInt,
                    token = selectedToken,
                ),
                providerAddress = provider,
            )

            else -> error("chain is invalid")
        }

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = (chain == Chain.MayaChain)
                    .let { if (it) 1.toBigInteger() else BigInteger.ZERO },
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createLeaveTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val memo = DepositMemo.Leave(
            nodeAddress = nodeAddress,
        )

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = (chain == Chain.MayaChain)
                    .let { if (it) 1.toBigInteger() else BigInteger.ZERO },
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createCustomTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val memo = DepositMemo.Custom(
            memo = customMemoFieldState.text.toString(),
        )

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount < BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = "",

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createTonDepositTransaction(memo: DepositMemo): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        if (depositChain != Chain.Ton) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.error_invalid_chain)
            )
        }

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
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
        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val tokenAmountInt =
            tokenAmount
                ?.movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createStakeTransaction(): DepositTransaction =
        createTonDepositTransaction(DepositMemo.Stake)

    private suspend fun createUnstakeTransaction(): DepositTransaction =
        createTonDepositTransaction(DepositMemo.Unstake)

    private suspend fun createMergeTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val mergeToken = state.value.selectedCoin
        val dstAddr = mergeToken.contract

        val memo = "merge:${mergeToken.denom}"

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount < BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,

            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createSwitchTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val dstAddr = nodeAddressFieldState.text.toString()

        val memo = "SWITCH:${thorAddressFieldState.text}"

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount < BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,

            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createTransferIbcTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val dstAddr = nodeAddressFieldState.text.toString()

        val memo = DepositMemo.TransferIbc(
            srcChain = chain,
            dstChain = state.value.selectedDstChain,
            dstAddress = dstAddr,

            memo = customMemoFieldState.text.toString()
                .takeIf { it.isNotBlank() },
        )

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount < BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER,
            )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private fun showError(text: UiText) {
        state.update { it.copy(errorText = text) }
    }

    private fun validateCustomMemo(memo: String): UiText? = if (memo.isBlank()) {
        UiText.StringResource(R.string.dialog_default_error_title)
    } else {
        null
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        val chain = chain ?: return UiText.StringResource(R.string.dialog_default_error_title)
        if (dstAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, dstAddress))
            return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    private fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal < BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

    private fun validateBasisPoints(basisPoints: Int?): UiText? {
        if (basisPoints == null || basisPoints <= 0 || basisPoints > 100) {
            return UiText.StringResource(R.string.send_from_invalid_amount)
        }
        return null
    }

    fun validateAssets() {
        val assets = assetsFieldState.text.toString()
        state.update {
            it.copy(
                assetsError = if (!isAssetCharsValid(assets))
                    UiText.StringResource(R.string.deposit_error_invalid_assets)
                else null
            )
        }
    }

    fun validateLpUnits() {
        val lpUnits = lpUnitsFieldState.text.toString()
        state.update {
            it.copy(
                lpUnitsError = if (!isLpUnitCharsValid(lpUnits))
                    UiText.StringResource(R.string.deposit_error_invalid_lpunits)
                else null
            )
        }
    }

    private fun isLpUnitCharsValid(lpUnits: String) =
        lpUnits.toIntOrNull() != null &&
                lpUnits.all { it.isDigit() } &&
                lpUnits.toInt() > 0


}

internal data class TokenMergeInfo(
    val denom: String,
    val contract: String,
)

private val tokensToMerge = listOf(
    TokenMergeInfo(
        denom = "thor.kuji",
        contract = "thor14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s3p2nzy"
    ),
    TokenMergeInfo(
        denom = "thor.rkuji",
        contract = "thor1yyca08xqdgvjz0psg56z67ejh9xms6l436u8y58m82npdqqhmmtqrsjrgh"
    ),
    TokenMergeInfo(
        denom = "thor.fuzn",
        contract = "thor1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsw5xx2d"
    ),
    TokenMergeInfo(
        denom = "thor.nstk",
        contract = "thor1cnuw3f076wgdyahssdkd0g3nr96ckq8cwa2mh029fn5mgf2fmcmsmam5ck"
    ),
    TokenMergeInfo(
        denom = "thor.wink",
        contract = "thor1yw4xvtc43me9scqfr2jr2gzvcxd3a9y4eq7gaukreugw2yd2f8tsz3392y"
    ),
    TokenMergeInfo(
        denom = "thor.lvn",
        contract = "thor1ltd0maxmte3xf4zshta9j5djrq9cl692ctsp9u5q0p9wss0f5lms7us4yf"
    ),
)