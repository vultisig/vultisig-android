package com.vultisig.wallet.presenter.settings.currency_unit_setting

sealed class CurrencyUnitSettingEvent{
    data class ChangeCurrencyUnit(val selectedCurrencyUnit: CurrencyUnitItem):CurrencyUnitSettingEvent()
    data object InitSelectedCurrencyUnit:CurrencyUnitSettingEvent()
}