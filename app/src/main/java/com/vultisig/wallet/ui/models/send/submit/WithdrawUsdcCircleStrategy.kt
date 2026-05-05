package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_CIRCLE_WITHDRAW
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

internal class WithdrawUsdcCircleStrategy(
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
    private val mscaAddressProvider: () -> String?,
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

                val nonDeFiAccount =
                    accountsRepository
                        .loadAddresses(vaultId)
                        .firstOrNull()
                        ?.flatMap { it.accounts }
                        ?.find { it.token.id.equals(Coins.Ethereum.ETH.id, true) }

                val nonDeFiBalance = nonDeFiAccount?.tokenValue?.value ?: BigInteger.ZERO

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

                val memo =
                    EthereumFunction.withdrawCircleMSCA(
                        vaultAddress =
                            nonDeFiAccount?.token?.address ?: error("Vault Address Empty"),
                        tokenAddress = Coins.Ethereum.USDC.contractAddress,
                        amount = tokenAmountInt,
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

                val nativeCoin = nonDeFiAccount.token

                val depositTx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = nativeCoin,
                        srcAddress = srcAddress,
                        dstAddress =
                            mscaAddressProvider()
                                ?: throw InvalidTransactionDataException(
                                    UiText.StringResource(R.string.send_error_msca_not_deployed)
                                ),
                        memo = memo,
                        srcTokenValue = TokenValue(value = BigInteger.ZERO, token = nativeCoin),
                        estimatedFees = gasFee,
                        estimateFeesFiat =
                            gasFeeToEstimatedFee
                                .fiatFeesFor(gasFee, selectedToken)
                                .formattedFiatValue,
                        blockChainSpecific = specific.blockChainSpecific,
                        operation = OPERATION_CIRCLE_WITHDRAW,
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
                showError(e.message?.asUiText() ?: UiText.Empty)
            } finally {
                hideLoading()
            }
        }
    }
}
