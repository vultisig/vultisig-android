package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.AppCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

internal interface AppCurrencyRepository {

    val currency: Flow<AppCurrency>

    fun setCurrency(currency: AppCurrency)

}

internal class AppCurrencyRepositoryImpl @Inject constructor() : AppCurrencyRepository {

    override val currency: Flow<AppCurrency> = flowOf(AppCurrency.USD)

    override fun setCurrency(currency: AppCurrency) {
        // TODO set up saving to preferences datastore when needed
    }

}