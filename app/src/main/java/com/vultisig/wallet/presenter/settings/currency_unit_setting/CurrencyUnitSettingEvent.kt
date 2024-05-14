package com.vultisig.wallet.presenter.settings.currency_unit_setting

import com.vultisig.wallet.presenter.settings.settings_main.CurrencyUnit

sealed class CurrencyUnitSettingEvent{
    data class ChangeCurrencyUnit(val selectedCurrencyUnit: CurrencyUnit):CurrencyUnitSettingEvent()
    data object InitScreen:CurrencyUnitSettingEvent()
}