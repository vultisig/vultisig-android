package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal
import java.math.BigInteger

data class Address(
    val chain: Chain,
    val address: String,
    val accounts: List<Account>
)

data class Account(
    val token: Coin,
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
    val price: FiatValue?,
)

fun List<Account>.calculateAccountsTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue = account.fiatValue ?: return@calculateAccountsTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }

fun List<Address>.calculateAddressesTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue = account.accounts.calculateAccountsTotalFiatValue()
            ?: return@calculateAddressesTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }