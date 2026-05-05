package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.ThorchainFunctions
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_RUJI_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_TCY_COMPOUND_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vultisig.keysign.v1.TransactionType

internal class StakeStrategy(
    private val scope: CoroutineScope,
    private val tokenAmountFieldState: TextFieldState,
    private val accountValidator: AccountValidator,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val accountsRepository: AccountsRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val isAutocompoundProvider: () -> Boolean,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (UiText) -> Unit,
) : SendSubmitStrategy {

    override fun submit() {
        scope.launch {
            showLoading()
            try {
                val validated = accountValidator.validate()
                val vaultId = validated.vaultId
                val chain = validated.chain
                val dstAddress = validated.dstAddress
                val selectedAccount = validated.selectedAccount
                val gasFee = validated.gasFee

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
                    when (defiTypeProvider()) {
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

                        else -> error("DeFi Type not supported ${defiTypeProvider()?.type}")
                    }

                depositTransactionRepository.addTransaction(depositTx)

                navigator.route(
                    Route.VerifyDeposit(transactionId = depositTx.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
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
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
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
        val isAutoCompound = isAutocompoundProvider()
        val stakingMemo = if (isAutoCompound) "" else "TCY+"

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
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = stakingPayload,
        )
    }
}
