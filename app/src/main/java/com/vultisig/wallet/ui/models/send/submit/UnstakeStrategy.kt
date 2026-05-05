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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vultisig.keysign.v1.TransactionType

internal class UnstakeStrategy(
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

                        else -> error("DeFi Type not supported ${defiTypeProvider()?.type}")
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
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
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
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
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

        val isAutoCompound = isAutocompoundProvider()
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
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = unstakePayload,
        )
    }
}
