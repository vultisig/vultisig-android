package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.ThorchainFunctions
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
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_YTCY_AFFILIATE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YTCY_CONTRACT
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
import vultisig.keysign.v1.TransactionType

internal class MintStrategy(
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
                        when (defiTypeProvider()) {
                            DeFiNavActions.MINT_YRUNE -> YRUNE_CONTRACT
                            DeFiNavActions.MINT_YTCY -> YTCY_CONTRACT
                            else ->
                                throw InvalidTransactionDataException(
                                    UiText.StringResource(R.string.dialog_default_error_body)
                                )
                        }

                    val depositTx =
                        DepositTransaction(
                            id = UUID.randomUUID().toString(),
                            vaultId = vaultId,
                            srcToken = selectedToken,
                            srcAddress = srcAddress,
                            dstAddress = dstAddress,
                            memo = depositMemo,
                            srcTokenValue =
                                TokenValue(value = tokenAmountInt, token = selectedToken),
                            estimatedFees = gasFee,
                            estimateFeesFiat =
                                gasFeeToEstimatedFee
                                    .fiatFeesFor(gasFee, selectedToken)
                                    .formattedFiatValue,
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    showError(
                        e.message?.asUiText()
                            ?: UiText.StringResource(R.string.dialog_default_error_body)
                    )
                } finally {
                    hideLoading()
                }
            }
    }
}
