package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.MarketWidgetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Glance widgets are instantiated by the framework, not by Hilt, so they reach the graph through
 * this entry point. Only the market data and currency repositories are exposed — the widgets have
 * no business near vaults or signing.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MarketWidgetEntryPoint {

    fun marketWidgetRepository(): MarketWidgetRepository

    fun appCurrencyRepository(): AppCurrencyRepository

    companion object {
        fun resolve(context: Context): MarketWidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MarketWidgetEntryPoint::class.java,
            )
    }
}
