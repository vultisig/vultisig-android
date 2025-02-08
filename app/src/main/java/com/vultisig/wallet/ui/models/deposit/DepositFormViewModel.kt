package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
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
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.ui.models.deposit.DepositChain.Maya
import com.vultisig.wallet.ui.models.deposit.DepositChain.Thor
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

internal enum class DepositOption {
    Bond,
    Unbond,
    Leave,
    DepositPool,
    WithdrawPool,
    Stake,
    Unstake,
    Custom,
}

internal enum class DepositChain {
    Maya, Thor, Ton;

    companion object {
        fun from(chain: Chain) = when (chain) {
            Chain.MayaChain -> Maya
            Chain.ThorChain -> Thor
            Chain.Ton -> Ton
            else -> error("chain is invalid")
        }
    }
}

@Immutable
internal data class DepositFormUiModel(
    val depositMessage: UiText = UiText.Empty,
    val depositOption: DepositOption = DepositOption.Bond,
    val depositOptions: List<DepositOption> = emptyList(),
    val depositChain: DepositChain? = null,
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

        val depositChain = DepositChain.from(chain)

        val depositOptions =
            when (chain) {
                Chain.ThorChain -> listOf(
                    DepositOption.Bond,
                    DepositOption.Unbond,
                    DepositOption.Leave,
                    DepositOption.DepositPool,
                    DepositOption.WithdrawPool,
                    DepositOption.Custom,
                )
                Chain.MayaChain -> listOf(
                    DepositOption.Bond,
                    DepositOption.Unbond,
                    DepositOption.Leave,
                    DepositOption.Custom,
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
                depositChain = depositChain
            )
        }

        viewModelScope.launch {
            accountsRepository.loadAddress(
                vaultId,
                chain
            ).collect { addresses ->
                    addresses.accounts.firstOrNull { it.token.isNativeToken }?.tokenValue?.let(mapTokenValueToStringWithUnit)
                        ?.let { tokenValue ->
                            state.update {
                                it.copy(balance = tokenValue.asUiText())
                            }
                        }
                }
        }
    }

    fun selectDepositOption(option: DepositOption) = viewModelScope.launch {
        resetTextFields()
        state.update {
            it.copy(depositOption = option)
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
                    DepositOption.DepositPool -> createDepositPoolTransaction()
                    DepositOption.WithdrawPool -> createWithdrawPoolTransaction()
                    DepositOption.Stake -> createStakeTransaction()
                    DepositOption.Unstake -> createUnstakeTransaction()
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

        if (depositChain == Thor && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Maya && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Maya && !isLpUnitCharsValid(lpUnits)) {
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
            ?.movePointRight(if (depositChain == Thor) 2 else 0)
            ?.toInt()

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo = when (depositChain) {
            Maya -> Bond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                lpUnits = lpUnits.toIntOrNull(),
                assets = assets
            )

            Thor -> Bond.Thor(
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

        if (depositChain == Maya && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Maya && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (depositChain == Thor && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
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
            Maya -> Unbond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                assets = assets,
                lpUnits = lpUnits.toIntOrNull(),
            )

            Thor -> Unbond.Thor(
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
                value = 1.toBigInteger(),
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
                value = 1.toBigInteger(),
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createDepositPoolTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

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
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val memo = DepositMemo.DepositPool

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

    private suspend fun createWithdrawPoolTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val basisPoints = basisPointsFieldState.text.toString()
            .toIntOrNull()

        validateBasisPoints(basisPoints)?.let {
            throw InvalidTransactionDataException(it)
        }

        val memo = DepositMemo.WithdrawPool(
            basisPoints = basisPoints!! * 100, // 10000 BP = 100%; basisPoints in 0..100
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
            dstAddress = "",

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = BigInteger.ZERO,
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

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
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

        if (depositChain != DepositChain.Ton) {
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

