package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
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
import com.vultisig.wallet.ui.models.send.toPlainBigDecimalOrNull
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    private val thorChainApi: ThorChainApi,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val isAutocompoundProvider: () -> Boolean,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (UiText) -> Unit,
) : SendSubmitStrategy {

    private var submitJob: Job? = null

    override fun submit() {
        if (submitJob?.isActive == true) return
        submitJob =
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

                    val tokenAmount =
                        tokenAmountFieldState.text.toString().toPlainBigDecimalOrNull()
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

                            DeFiNavActions.UNSTAKE_SRUJI ->
                                createRujiCompoundUnstakeDepositTransaction(
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
                                        ?: throw InvalidTransactionDataException(
                                            UiText.StringResource(R.string.send_error_no_token)
                                        )

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

                            else ->
                                throw InvalidTransactionDataException(
                                    UiText.StringResource(R.string.dialog_default_error_body)
                                )
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
                    showError(
                        e.message?.asUiText()
                            ?: UiText.StringResource(R.string.dialog_default_error_body)
                    )
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

    /**
     * Redeems from the auto-compounding RUJI position (`liquid.unbond`).
     *
     * The form is denominated in RUJI so it matches the card the user tapped, but the contract is
     * funded with sRUJI receipt *shares*, so the amount is converted at the pool's live share
     * price. Shares and size are read from the same response, so their ratio is self-consistent;
     * redeeming the whole position sends the exact share balance rather than a rounded conversion,
     * so no dust is stranded. Rounding is downward everywhere else, so the redemption can never
     * exceed what is held even if the share price moves between the form loading and this submit.
     */
    private suspend fun createRujiCompoundUnstakeDepositTransaction(
        vaultId: String,
        selectedToken: Coin,
        srcAddress: String,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        gasFee: TokenValue,
        chain: Chain,
    ): DepositTransaction {
        // The read fails closed on an unreadable position rather than reporting a false zero, so a
        // failure here is a fetch problem, not a balance problem. Translate it: submit()'s generic
        // catch would otherwise surface the raw "Could not fetch balances: status …" text.
        val stakeBalances =
            withContext(Dispatchers.IO) {
                    runCatching { thorChainApi.getRujiStakeBalance(srcAddress) }
                }
                .getOrElse { error: Throwable ->
                    if (error is CancellationException) throw error
                    Timber.e(error, "Failed to read the RUJI staking position for the unbond")
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.dialog_default_error_body)
                    )
                }
        val positionValue = stakeBalances.autoCompoundAmount

        // An unreadable share count is not an empty position: sizing a redemption off a guessed
        // count is exactly what the null guards against, so stop here instead.
        val heldShares =
            stakeBalances.autoCompoundShares
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.dialog_default_error_body)
                )

        if (positionValue <= BigInteger.ZERO || heldShares <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_insufficient_balance)
            )
        }

        val shares =
            if (tokenAmountInt >= positionValue) {
                heldShares
            } else {
                tokenAmountInt.multiply(heldShares).divide(positionValue)
            }

        if (shares < BigInteger.ONE) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
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
                    transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                )
            }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            memo = "",
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat =
                gasFeeToEstimatedFee.fiatFeesFor(gasFee, selectedToken).formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload =
                ThorchainFunctions.unstakeRujiCompound(
                    shares = shares,
                    stakingContract = STAKING_RUJI_CONTRACT,
                    fromAddress = srcAddress,
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
        val isAutoCompound = isAutocompoundProvider()
        val unstakeMemo =
            if (isAutoCompound) {
                ""
            } else {
                val basisPoints =
                    if (totalTokenAmount > BigInteger.ZERO) {
                        tokenAmountInt
                            .multiply(BigInteger.valueOf(10_000))
                            .divide(totalTokenAmount)
                            .toInt()
                            .coerceIn(0, 10_000)
                    } else {
                        10_000
                    }
                ThorchainFunctions.tcyUnstakeMemo(basisPoints)
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
