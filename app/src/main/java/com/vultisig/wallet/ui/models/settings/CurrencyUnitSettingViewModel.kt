package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class CurrencyUnitSettingUiModel(
    val currencyUnits: List<CurrencyUnit> = listOf(),
    val selectedCurrency: CurrencyUnit = CurrencyUnit(),
)

@HiltViewModel
internal class CurrencyUnitSettingViewModel @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository
) : ViewModel() {

    val state = MutableStateFlow(
        CurrencyUnitSettingUiModel(
            currencyUnits = appCurrencyRepository.getAllCurrencies().map {
                CurrencyUnit(
                    name = it.ticker,
                    fullName = it.fullName
                )
            })
    )


    fun initScreenUnit() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect {
                state.update { state: CurrencyUnitSettingUiModel ->
                    state.copy(
                        selectedCurrency = CurrencyUnit(
                            name = it.ticker,
                            fullName = it.fullName
                        ),
                    )
                }
            }
        }
    }

    fun changeCurrencyUnit(currencyUnit: CurrencyUnit) {
        viewModelScope.launch {
            val currency = AppCurrency.fromTicker(currencyUnit.name)
                ?: return@launch
            appCurrencyRepository.setCurrency(currency)
        }
    }

}