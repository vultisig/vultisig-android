package com.vultisig.wallet.presenter.settings.currency_unit_setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CurrencyUnitSettingViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val selectedCurrencyUnitId = savedStateHandle.get<Int>(Destination.CurrencyUnitSetting.ARG_CURRENCY_ID)?:0
    val state = MutableStateFlow(CurrencyUnitSettingUiModel())

    init {
        state.update {
            it.copy(
                currencyUnits = listOf(
                    CurrencyUnitItem(id = 0,"USD"),
                    CurrencyUnitItem(id = 1,"AUD"),
                ),
                selectedCurrencyUnitId = 0
            )
        }
    }

    fun onEvent(event: CurrencyUnitSettingEvent) {
        when (event) {
            is CurrencyUnitSettingEvent.ChangeCurrencyUnit -> changeCurrencyUnit(event.selectedCurrencyUnit)
            CurrencyUnitSettingEvent.InitSelectedCurrencyUnit -> initSelectedCurrencyUnit()
        }
    }

    private fun initSelectedCurrencyUnit() {
        state.update {
            it.copy(selectedCurrencyUnitId = selectedCurrencyUnitId)
        }
    }

    private fun changeCurrencyUnit(currencyUnit: CurrencyUnitItem) {
        state.update {
            it.copy(selectedCurrencyUnitId = currencyUnit.id)
        }
    }

}