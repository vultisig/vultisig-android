package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.widgets.market.MarketWidgetRefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CurrencyUnitSettingUiModel(
    val currencyUnits: List<CurrencyUnit> = listOf(),
    val selectedCurrency: CurrencyUnit = CurrencyUnit(),
)

@HiltViewModel
internal class CurrencyUnitSettingViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    val state =
        MutableStateFlow(
            CurrencyUnitSettingUiModel(
                currencyUnits =
                    appCurrencyRepository.getAllCurrencies().map {
                        CurrencyUnit(name = it.ticker, fullName = it.fullName)
                    }
            )
        )

    fun initScreenUnit() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect {
                state.update { state: CurrencyUnitSettingUiModel ->
                    state.copy(
                        selectedCurrency = CurrencyUnit(name = it.ticker, fullName = it.fullName)
                    )
                }
            }
        }
    }

    fun changeCurrencyUnit(currencyUnit: CurrencyUnit) {
        viewModelScope.launch {
            val currency = AppCurrency.fromTicker(currencyUnit.name) ?: return@launch
            appCurrencyRepository.setCurrency(currency)
            // Home-screen market widgets price in the app currency; re-pull so they don't show
            // the old one until their next scheduled poll.
            MarketWidgetRefreshWorker.refreshNow(context, replaceQueued = true)
        }
    }
}
