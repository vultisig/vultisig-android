package com.vultisig.wallet.data.models

import com.vultisig.wallet.models.Coin
import java.math.BigDecimal

internal data class Account(
    val token: Coin,
    val chainName: String,
    val logo: Int,
    val address: String,
    /**
    amount of native token for this chain on the address,
    null if unknown yet
     */
    val tokenAmount: String?,
    /**
    amount of token for this chain on the address in fiat,
    null if unknown yet
     */
    val fiatValue: FiatValue?,
)

internal fun List<Account>.calculateTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue = account.fiatValue ?: return@calculateTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }