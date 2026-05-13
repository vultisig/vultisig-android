package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.asAddressInput
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class AmountFractionManager(
    private val scope: CoroutineScope,
    private val tokenAmountFieldState: TextFieldState,
    private val addressFieldState: TextFieldState,
    private val memoFieldState: TextFieldState,
    private val uiState: MutableStateFlow<SendFormUiModel>,
    private val gasFee: MutableStateFlow<TokenValue?>,
    private val gasSettings: StateFlow<GasSettings?>,
    private val specific: StateFlow<BlockChainSpecificAndUtxo?>,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val vaultProvider: () -> Vault?,
    private val accountProvider: () -> Account?,
    private val currentTronFrozenBalanceProvider: () -> BigDecimal?,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val adjustGasFee: (TokenValue, GasSettings?, BlockChainSpecificAndUtxo?) -> TokenValue,
    private val amountManager: AmountManager,
) {
    private var chooseAmountFractionJob: Job? = null

    /**
     * Cancels any in-flight percentage/max calculation so callers (e.g. the Tron resource toggle)
     * can clear amount fields without an older calc resuming and clobbering them after the
     * suspension point in `calculatePercentageWithAccurateFee`.
     *
     * Also clears `isAmountSelectionLoading` directly — the cancelled job's `finally` skips the
     * reset (the `isActive` guard is false for cancelled jobs), so external cancellers would
     * otherwise leave the spinner stuck on.
     */
    fun cancel() {
        chooseAmountFractionJob?.cancel()
        chooseAmountFractionJob = null
        uiState.update { it.copy(isAmountSelectionLoading = false) }
    }

    fun chooseMaxTokenAmount() {
        if (
            defiTypeProvider() == DeFiNavActions.UNFREEZE_TRX &&
                uiState.value.isTronFrozenBalancesLoading
        ) {
            return
        }
        chooseAmountFractionJob?.cancel()
        chooseAmountFractionJob =
            scope.launch {
                uiState.update {
                    it.copy(
                        selectedAmountFraction = AmountFraction.F100,
                        isAmountSelectionLoading = true,
                    )
                }
                val amount =
                    try {
                        calculatePercentageWithAccurateFee(1f)
                    } finally {
                        // Cancelled job's finally still runs, but if a newer selection is
                        // in flight, that one owns the loading flag now — isActive is false
                        // for cancelled jobs and true for normal completion.
                        if (currentCoroutineContext().isActive) {
                            uiState.update { it.copy(isAmountSelectionLoading = false) }
                        }
                    }
                // If a newer choose*Amount call cancelled this job after the last suspension
                // point, abort before applying — otherwise the older selection would clobber
                // the newer one.
                currentCoroutineContext().ensureActive()
                amountManager.markMax(amount)
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amount.toPlainString())
            }
    }

    fun choosePercentageAmount(amountFraction: AmountFraction) {
        if (
            defiTypeProvider() == DeFiNavActions.UNFREEZE_TRX &&
                uiState.value.isTronFrozenBalancesLoading
        ) {
            return
        }
        chooseAmountFractionJob?.cancel()
        chooseAmountFractionJob =
            scope.launch {
                uiState.update {
                    it.copy(
                        selectedAmountFraction = amountFraction,
                        isAmountSelectionLoading = true,
                    )
                }
                val amount =
                    try {
                        calculatePercentageWithAccurateFee(amountFraction.value)
                    } finally {
                        if (currentCoroutineContext().isActive) {
                            uiState.update { it.copy(isAmountSelectionLoading = false) }
                        }
                    }
                currentCoroutineContext().ensureActive()
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amount.toPlainString())
            }
    }

    private suspend fun calculatePercentageWithAccurateFee(percentage: Float): BigDecimal {
        val vault = vaultProvider() ?: return BigDecimal.ZERO
        val isMax = percentage == 1f
        val selectedAccount = accountProvider() ?: return BigDecimal.ZERO
        val token = selectedAccount.token
        val defiType = defiTypeProvider()

        if (defiType == DeFiNavActions.UNFREEZE_TRX) {
            return frozenTrxFraction(percentage, token.decimal)
        }

        var amount =
            if (gasFee.value != null) {
                fetchPercentageOfAvailableBalance(percentage)
            } else {
                getAvailableTokenBalance(selectedAccount, BigInteger.ZERO)
                    ?.decimal
                    ?.multiply(percentage.toBigDecimal()) ?: BigDecimal.ZERO
            }

        if (
            defiType != null &&
                defiType != DeFiNavActions.BOND &&
                defiType != DeFiNavActions.STAKE_RUJI &&
                defiType != DeFiNavActions.UNSTAKE_RUJI &&
                defiType != DeFiNavActions.STAKE_TCY &&
                defiType != DeFiNavActions.UNSTAKE_TCY &&
                defiType != DeFiNavActions.STAKE_STCY &&
                defiType != DeFiNavActions.UNSTAKE_STCY &&
                defiType != DeFiNavActions.MINT_YRUNE &&
                defiType != DeFiNavActions.REDEEM_YRUNE &&
                defiType != DeFiNavActions.MINT_YTCY &&
                defiType != DeFiNavActions.REDEEM_YTCY &&
                defiType != DeFiNavActions.FREEZE_TRX &&
                defiType != DeFiNavActions.UNFREEZE_TRX
        ) {
            return amount
        }

        val chain = token.chain

        if (gasFee.value != null && chain.standard == TokenStandard.EVM) {
            return amount
        }

        try {
            val tokenAmountInt = amount.movePointRight(token.decimal).toBigInteger()
            val blockchainTransaction =
                Transfer(
                    coin = token,
                    vault =
                        VaultData(
                            vaultHexChainCode = vault.hexChainCode,
                            vaultHexPublicKey = vault.getPubKeyByChain(chain),
                        ),
                    amount = tokenAmountInt,
                    to = addressFieldState.text.asAddressInput(),
                    memo = memoFieldState.text.toString(),
                    isMax = isMax,
                )

            val calculatedFee =
                withContext(Dispatchers.IO) {
                    feeServiceComposite.calculateFees(blockchainTransaction)
                }

            val nativeCoin =
                withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }

            val newGasFee = TokenValue(value = calculatedFee.amount, token = nativeCoin)

            gasFee.value = adjustGasFee(newGasFee, gasSettings.value, specific.value)
            amount = fetchPercentageOfAvailableBalance(percentage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to calculate gas fee for percentage amount")
        }

        return amount
    }

    private suspend fun fetchPercentageOfAvailableBalance(percentage: Float): BigDecimal {
        val selectedAccount = accountProvider() ?: return BigDecimal.ZERO
        val currentGasFee = gasFee.value ?: return BigDecimal.ZERO
        val defiType = defiTypeProvider()

        if (defiType == DeFiNavActions.UNFREEZE_TRX) {
            return frozenTrxFraction(percentage, selectedAccount.token.decimal)
        }

        val availableTokenBalance =
            if (
                defiType == null ||
                    defiType == DeFiNavActions.BOND ||
                    defiType == DeFiNavActions.STAKE_RUJI ||
                    defiType == DeFiNavActions.STAKE_TCY ||
                    defiType == DeFiNavActions.STAKE_STCY ||
                    defiType == DeFiNavActions.MINT_YRUNE ||
                    defiType == DeFiNavActions.REDEEM_YRUNE ||
                    defiType == DeFiNavActions.MINT_YTCY ||
                    defiType == DeFiNavActions.REDEEM_YTCY ||
                    defiType == DeFiNavActions.FREEZE_TRX
            ) {
                getAvailableTokenBalance(selectedAccount, currentGasFee.value)
            } else {
                getAvailableTokenBalance(
                    selectedAccount,
                    BigInteger.ZERO, // Subtraction should not happen to DeFi Balance (Unbond,
                    // Staked, Rewards, etc...)
                )
            }

        return availableTokenBalance
            ?.decimal
            ?.multiply(percentage.toBigDecimal())
            ?.setScale(selectedAccount.token.decimal, RoundingMode.DOWN)
            ?.stripTrailingZeros() ?: BigDecimal.ZERO
    }

    private fun frozenTrxFraction(percentage: Float, decimals: Int): BigDecimal {
        val frozen = currentTronFrozenBalanceProvider() ?: return BigDecimal.ZERO
        return frozen
            .multiply(percentage.toBigDecimal())
            .setScale(decimals, RoundingMode.DOWN)
            .stripTrailingZeros()
    }
}
