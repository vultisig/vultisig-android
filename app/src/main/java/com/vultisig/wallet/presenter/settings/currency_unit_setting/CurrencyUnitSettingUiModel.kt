package com.vultisig.wallet.presenter.settings.currency_unit_setting

import com.vultisig.wallet.presenter.settings.settings_main.CurrencyUnit


data class CurrencyUnitSettingUiModel(
    val currencyUnits: List<CurrencyUnit> = listOf(),
    val selectedCurrency: CurrencyUnit = CurrencyUnit(),
)