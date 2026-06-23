package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import wallet.core.jni.proto.Bitcoin

/**
 * Owns the amount / balance / token↔fiat conversion pipeline extracted from `DepositFormViewModel`:
 * the two-way amount-conversion collector, the displayed-balance updater, the deposit-amount
 * validator and the gas/fee bridge into [DepositGasFeeHelper].
 *
 * The mapper and gas helper are Hilt-injected here; the ViewModel keeps `viewModelScope` ownership
 * and supplies it (assisted) along with the form [fields], the [appCurrency] flow, the shared
 * [state] updater and the [chain] / [vaultId] accessors so this helper never owns its own scope or
 * VM state.
 */
internal class DepositAmountHelper
@AssistedInject
constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val gasFeeHelper: DepositGasFeeHelper,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val fields: DepositFieldStates,
    @Assisted private val appCurrency: StateFlow<AppCurrency>,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val chain: () -> Chain?,
    @Assisted private val vaultId: () -> String?,
) {

    /** @see DepositAmountHelper */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DepositAmountHelper] bound to the given scope, form fields, currency flow,
         * shared state updater and chain/vault accessors.
         */
        fun create(
            scope: CoroutineScope,
            fields: DepositFieldStates,
            appCurrency: StateFlow<AppCurrency>,
            state: MutableStateFlow<DepositFormUiModel>,
            chain: () -> Chain?,
            vaultId: () -> String?,
        ): DepositAmountHelper
    }

    private val tokenAmountFieldState
        get() = fields.tokenAmountFieldState

    private val fiatAmountFieldState
        get() = fields.fiatAmountFieldState

    private var lastTokenValueUserInput: String = ""
    private var lastFiatValueUserInput: String = ""
    private var amountChangesJob: Job? = null

    /**
     * Starts the two-way token↔fiat amount binding: when the user edits the token field the fiat
     * field is recomputed (and vice-versa), guarding against feedback loops via the
     * [lastTokenValueUserInput] / [lastFiatValueUserInput] tracking and a single-collector
     * [amountChangesJob]. Idempotent — a second call while the collector is running is a no-op.
     */
    fun collectAmountChanges() {
        if (amountChangesJob != null) return
        amountChangesJob =
            scope.safeLaunch {
                combine(
                        state.map { it.selectedToken }.distinctUntilChanged(),
                        tokenAmountFieldState.textAsFlow(),
                        fiatAmountFieldState.textAsFlow(),
                    ) { selectedToken, tokenFieldValue, fiatFieldValue ->
                        val tokenString = tokenFieldValue.toString()
                        val fiatString = fiatFieldValue.toString()
                        if (lastTokenValueUserInput != tokenString) {
                            val fiatValue =
                                convertAmountValue(tokenString, selectedToken) { value, price ->
                                        value
                                            .multiply(price)
                                            .setScale(selectedToken.decimal, RoundingMode.DOWN)
                                            .stripTrailingZeros()
                                    }
                                    ?.takeIf { it.isNotEmpty() } ?: return@combine
                            lastTokenValueUserInput = tokenString
                            lastFiatValueUserInput = fiatValue
                            fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                        } else if (lastFiatValueUserInput != fiatString) {
                            val tokenValue =
                                convertAmountValue(fiatString, selectedToken) { value, price ->
                                        value.divide(
                                            price,
                                            selectedToken.decimal,
                                            RoundingMode.DOWN,
                                        )
                                    }
                                    ?.takeIf { it.isNotEmpty() } ?: return@combine
                            lastTokenValueUserInput = tokenValue
                            lastFiatValueUserInput = fiatString
                            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                        }
                    }
                    .collect()
            }
    }

    /**
     * Refreshes the displayed balance and clears/sets the amount error for the resolved [account]
     * on [chain]. A null account surfaces a "must be enabled" error keyed on [targetTicker].
     */
    suspend fun updateTokenAmount(
        account: Account?,
        chain: Chain,
        targetTicker: String?,
        vaultId: String,
    ) {
        if (account != null) {
            val tokenValue = account.tokenValue
            if (tokenValue != null) {
                val value = mapTokenValueToStringWithUnit(tokenValue)
                state.update { state ->
                    state.copy(
                        amountError = null,
                        balance = value.asUiText(),
                        balanceDecimal = tokenValue.decimal,
                    )
                }
            } else {
                // Account exists in vault but balance not yet loaded — clear stale error and
                // balance
                state.update {
                    it.copy(amountError = null, balance = UiText.Empty, balanceDecimal = null)
                }
            }
        } else {
            state.update {
                it.copy(
                    balance = UiText.Empty,
                    balanceDecimal = null,
                    amountError =
                        UiText.FormattedText(
                            R.string.must_be_enabled_before_proceeding,
                            listOf(targetTicker.orEmpty()),
                        ),
                )
            }
        }
    }

    /**
     * Validates the entered token amount against the selected/native balances and gas, returning
     * the amount in base units.
     *
     * @throws InvalidTransactionDataException when the amount is missing/zero, the native token is
     *   absent, or the balance is insufficient to cover the amount and/or gas.
     */
    fun requireTokenAmount(
        selectedToken: Coin,
        selectedAccount: Account,
        address: Address,
        gas: TokenValue,
    ): BigInteger {
        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt = tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

        val nativeTokenAccount =
            address.accounts.find { it.token.isNativeToken && it.token.chain == chain() }
        val nativeTokenValue =
            nativeTokenAccount?.tokenValue?.value
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        if (selectedToken.isNativeToken) {
            // Native-token deposits pay the amount and the gas from the same balance, so validate
            // amount + gas in-form; otherwise a full-balance amount fails late at signing.
            if (nativeTokenValue < tokenAmountInt + gas.value) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )
            }
        } else {
            if ((selectedAccount.tokenValue?.value ?: BigInteger.ZERO) < tokenAmountInt) {

                // For all other operations, or if the unstakable check failed
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )
            }

            if (nativeTokenValue < gas.value) {
                throw InvalidTransactionDataException(
                    UiText.FormattedText(
                        R.string.insufficient_native_token,
                        listOf(nativeTokenAccount.token.ticker),
                    )
                )
            }
        }

        return tokenAmountInt
    }

    /** Maps a raw [gasFee] into its estimated fiat-valued representation for the active chain. */
    suspend fun getFeesFiatValue(
        specific: BlockChainSpecificAndUtxo,
        gasFee: TokenValue,
        selectedToken: Coin,
    ): EstimatedGasFee = gasFeeHelper.getFeesFiatValue(chain(), specific, gasFee, selectedToken)

    /**
     * Calculates the native-token gas fee for a deposit on [chain] originating from [srcAddress].
     */
    suspend fun calculateGasFee(chain: Chain, token: Coin, srcAddress: String): TokenValue =
        gasFeeHelper.calculateGasFee(
            vaultId = vaultId() ?: error("Vault ID not set"),
            chain = chain,
            token = token,
            srcAddress = srcAddress,
        )

    /** Builds the Bitcoin (UTXO) transaction plan used to size fees for a secured-asset deposit. */
    suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan =
        gasFeeHelper.getBitcoinTransactionPlan(
            vaultId = vaultId,
            selectedToken = selectedToken,
            dstAddress = dstAddress,
            tokenAmountInt = tokenAmountInt,
            specific = specific,
            memo = memo,
        )

    private suspend fun convertAmountValue(
        value: String,
        token: Coin,
        transform: (value: BigDecimal, price: BigDecimal) -> BigDecimal,
    ): String? = gasFeeHelper.convertAmountValue(value, token, appCurrency.value, transform)
}
