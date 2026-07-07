package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.DepositMemo.Bond
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AddressParserRepository
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
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class BondStrategy(
    private val scope: CoroutineScope,
    private val tokenAmountFieldState: TextFieldState,
    private val providerBondFieldState: TextFieldState,
    private val operatorFeesBondFieldState: TextFieldState,
    private val accountValidator: AccountValidator,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val addressParserRepository: AddressParserRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
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

                    val providerAddress =
                        if (providerBondFieldState.text.toString().isNotEmpty()) {
                            try {
                                addressParserRepository.resolveName(
                                    providerBondFieldState.text.toString(),
                                    chain,
                                )
                            } catch (e: CancellationException) {
                                throw e
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
                    val operatorFeeValue: Int? =
                        if (feeBondOperator.isNotEmpty()) {
                            feeBondOperator.toIntOrNull()?.takeIf { it in 0..10000 }
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

                    val tokenAmount =
                        tokenAmountFieldState.text.toString().toPlainBigDecimalOrNull()
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
                            srcTokenValue =
                                TokenValue(value = tokenAmountInt, token = selectedToken),
                            estimatedFees = gasFee,
                            estimateFeesFiat =
                                gasFeeToEstimatedFee
                                    .fiatFeesFor(gasFee, selectedToken)
                                    .formattedFiatValue,
                            blockChainSpecific = specific.blockChainSpecific,
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
