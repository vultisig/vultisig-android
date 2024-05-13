package com.vultisig.wallet.presenter.settings.currency_unit_setting


data class CurrencyUnitSettingUiModel(
    val currencyUnits: List<CurrencyUnitItem> = listOf(),
    val selectedCurrencyUnitId: Int = 0,
)

data class CurrencyUnitItem(
    val id: Int = 0,
    val name: String = "",
)