@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.RippleHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.hasReaping
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.toValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/** Validates chain-specific transaction constraints for the send form. */
internal class ChainValidationService @Inject constructor() {

    // 1 ADA = 1,000,000 lovelace; kept as a local constant to avoid a WalletCore JNI call
    // (CoinTypeConfiguration.getDecimals) which is unavailable in unit tests.
    private val cardanoLovelaceDecimals = 6

    /**
     * Validates a slippage percentage string.
     *
     * @return A [UiText] error, or null if the value is valid.
     */
    fun validateSlippage(slippage: String?): UiText? {
        if (slippage.isNullOrBlank()) {
            return UiText.StringResource(R.string.slippage_required_error)
        }

        return try {
            val value = slippage.toBigDecimal()
            if (value < BigDecimal.ZERO || value > BigDecimal("100")) {
                UiText.StringResource(R.string.slippage_invalid_error)
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            UiText.StringResource(R.string.slippage_format_error)
        }
    }

    /**
     * Converts a slippage percentage string to its decimal form (e.g. "1.0" → "0.01").
     *
     * Defaults to "0.01" if parsing fails.
     */
    fun formatSlippage(slippage: String): String {
        val divider = "100".toBigDecimal()
        return try {
            slippage.toBigDecimal().setScale(2, RoundingMode.DOWN).divide(divider).toPlainString()
        } catch (_: NumberFormatException) {
            "0.01"
        }
    }

    /**
     * Validates Cardano-specific UTXO constraints.
     *
     * Throws [InvalidTransactionDataException] if the amount, balance, or change output would
     * violate the minimum UTXO value requirement.
     */
    fun validateCardanoUTXORequirements(
        sendAmount: BigInteger,
        totalBalance: BigInteger,
        estimatedFee: BigInteger,
    ) {
        val minUTXOValue: BigInteger = Chain.Cardano.getDustThreshold

        // 1. Check send amount meets minimum
        if (sendAmount < minUTXOValue) {
            val minAmountADA = cardanoLovelaceToAda(minUTXOValue)
            throw InvalidTransactionDataException(
                UiText.FormattedText(R.string.minimum_send_amount_is_ada, listOf(minAmountADA))
            )
        }

        // 2. Check sufficient balance
        val totalNeeded = sendAmount + estimatedFee
        if (totalBalance < totalNeeded) {
            val totalBalanceADA = cardanoLovelaceToAda(totalBalance)
            val errorMessage =
                if (totalBalance > estimatedFee && totalBalance > BigInteger.ZERO) {
                    UiText.FormattedText(
                        R.string.insufficient_balance_try_send,
                        listOf(totalBalanceADA),
                    )
                } else {
                    UiText.FormattedText(R.string.insufficient_balance_ada, listOf(totalBalanceADA))
                }
            throw InvalidTransactionDataException(errorMessage)
        }

        // 3. Check remaining balance (change) meets minimum UTXO requirement
        val remainingBalance = totalBalance - sendAmount - estimatedFee
        if (remainingBalance > BigInteger.ZERO && remainingBalance < minUTXOValue) {
            val totalBalanceADA = cardanoLovelaceToAda(totalBalance)
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.this_amount_would_leave_too_little_change,
                    listOf(totalBalanceADA),
                )
            )
        }
    }

    private fun cardanoLovelaceToAda(lovelace: BigInteger): BigDecimal =
        lovelace.toBigDecimal().divide(BigDecimal.TEN.pow(cardanoLovelaceDecimals))

    /**
     * Validates a BTC-like UTXO send amount against the dust threshold and transaction plan.
     *
     * Throws [InvalidTransactionDataException] if the amount is below dust or the plan has errors.
     */
    fun validateBtcLikeAmount(
        tokenAmountInt: BigInteger,
        chain: Chain,
        plan: Bitcoin.TransactionPlan?,
    ) {
        val minAmount = chain.getDustThreshold
        if (tokenAmountInt < minAmount) {
            val symbol = chain.coinType.symbol
            val name = chain.raw
            val formattedMinAmount = chain.toValue(minAmount).toString()
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.send_form_minimum_send_amount_is_requires_this,
                    listOf(formattedMinAmount, symbol, name),
                )
            )
        }

        if (plan?.error != SigningError.OK) {
            Timber.e("BTC-like transaction plan error: %s", plan?.error)
            throw InvalidTransactionDataException(R.string.insufficient_utxos_error.asUiText())
        }
    }

    /**
     * Checks whether the send would leave the source account below the existential deposit on
     * reaping-sensitive chains (Polkadot, Ripple).
     *
     * @return A [UiText] warning, or null if there is no reaping risk.
     */
    fun checkIsReapable(
        selectedAccount: Account?,
        selectedToken: Coin,
        tokenAmount: String,
        gasFee: TokenValue,
    ): UiText? {
        if (selectedAccount == null) return null
        val selectedChain = selectedToken.chain
        if (!selectedChain.hasReaping) return null

        val balance = selectedAccount.tokenValue?.value ?: BigInteger.ZERO
        val tokenAmountInt =
            tokenAmount.toBigDecimalOrNull()?.movePointRight(selectedToken.decimal)?.toBigInteger()
                ?: BigInteger.ZERO

        val existentialDeposit =
            when {
                selectedChain == Chain.Polkadot &&
                    selectedToken.ticker == Coins.Polkadot.DOT.ticker ->
                    PolkadotHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()

                selectedChain == Chain.Ripple && selectedToken.ticker == Coins.Ripple.XRP.ticker ->
                    RippleHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()

                else -> return null
            }

        if (balance - (gasFee.value + tokenAmountInt) >= existentialDeposit) return null

        return when (selectedChain) {
            Chain.Polkadot -> UiText.StringResource(R.string.send_form_polka_reaping_warning)
            Chain.Ripple -> UiText.StringResource(R.string.send_form_ripple_reaping_warning)
            else -> null
        }
    }

    /**
     * Replaces the UTXOs in [specific] with those from [plan], if available.
     *
     * No-ops for non-UTXO chains or when [plan] is null.
     */
    fun selectUtxosIfNeeded(
        chain: Chain,
        specific: BlockChainSpecificAndUtxo,
        plan: Bitcoin.TransactionPlan?,
    ): BlockChainSpecificAndUtxo {
        specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        val updatedUtxo =
            plan?.utxosOrBuilderList?.map { planUtxo ->
                UtxoInfo(
                    hash = planUtxo.outPoint.hash.toByteArray().reversedArray().toHexString(),
                    index = planUtxo.outPoint.index.toUInt(),
                    amount = planUtxo.amount,
                )
            } ?: return specific

        return specific.copy(utxos = updatedUtxo)
    }
}
