package com.vultisig.wallet.presenter.settings.currency_unit_setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AppCurrency.Companion.fromTicker
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.presenter.settings.settings_main.CurrencyUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class CurrencyUnitSettingViewModel @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository
) : ViewModel() {

    val state = MutableStateFlow(
        CurrencyUnitSettingUiModel(
            currencyUnits = appCurrencyRepository.getAllCurrencies().map { CurrencyUnit(it.ticker) })
    )


    fun onEvent(event: CurrencyUnitSettingEvent) {
        when (event) {
            is CurrencyUnitSettingEvent.ChangeCurrencyUnit -> changeCurrencyUnit(event.selectedCurrencyUnit)
            CurrencyUnitSettingEvent.InitScreen -> initScreenUnit()
        }
    }

    private fun initScreenUnit() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect {
                state.update { state: CurrencyUnitSettingUiModel ->
                    state.copy(
                        selectedCurrency = CurrencyUnit(it.ticker),
                    )
                }
            }
        }
    }

    private fun changeCurrencyUnit(currencyUnit: CurrencyUnit) {
        viewModelScope.launch {
            appCurrencyRepository.setCurrency(currencyUnit.name.fromTicker())
        }
    }

}