package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Builds a Stake [DepositTransaction] for the TON chain. */
internal class StakeStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val nodeAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
) : DepositSubmitStrategy {

    override suspend fun build(): DepositTransaction =
        buildTonDepositTransaction(
            memo = DepositMemo.Stake,
            vaultIdProvider = vaultIdProvider,
            chainProvider = chainProvider,
            stateProvider = stateProvider,
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = calculateGasFee,
            getFeesFiatValue = getFeesFiatValue,
        )
}

/**
 * Builds a TON deposit [DepositTransaction] carrying [memo].
 *
 * Shared by the Stake and Unstake strategies, which differ only by the memo they send.
 *
 * @param memo the deposit memo to attach (e.g. [DepositMemo.Stake] or [DepositMemo.Unstake]).
 */
internal suspend fun buildTonDepositTransaction(
    memo: DepositMemo,
    vaultIdProvider: () -> String?,
    chainProvider: () -> Chain?,
    stateProvider: () -> DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    accountsRepository: AccountsRepository,
    chainAccountAddressRepository: ChainAccountAddressRepository,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    getFeesFiatValue: suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
): DepositTransaction {
    val vaultId =
        requireNotNull(vaultIdProvider()) {
            "vaultId must be initialized before creating transaction"
        }
    val chain =
        chainProvider()
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

    val depositChain = stateProvider().depositChain

    if (depositChain != Chain.Ton) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.error_invalid_chain))
    }

    val nodeAddress = nodeAddressFieldState.text.toString()

    if (nodeAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, nodeAddress)) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.send_error_no_address))
    }

    val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

    if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.send_error_no_amount))
    }
    val address = accountsRepository.loadAddress(vaultId, chain).first()

    val selectedToken =
        address.accounts.firstOrNull { it.token.isNativeToken }?.token
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

    val tokenAmountInt =
        tokenAmount.movePointRight(selectedToken.decimal)?.toBigInteger() ?: BigInteger.ONE

    val srcAddress = selectedToken.address

    val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

    val specific =
        blockChainSpecificRepository.getSpecific(
            chain,
            srcAddress,
            selectedToken,
            gasFee,
            isSwap = false,
            isMaxAmountEnabled = false,
            isDeposit = true,
        )

    val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

    return DepositTransaction(
        id = UUID.randomUUID().toString(),
        vaultId = vaultId,
        srcToken = selectedToken,
        srcAddress = srcAddress,
        dstAddress = nodeAddress,
        memo = memo.toString(),
        srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
        estimatedFees = gasFee,
        estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        blockChainSpecific = specific.blockChainSpecific,
    )
}
