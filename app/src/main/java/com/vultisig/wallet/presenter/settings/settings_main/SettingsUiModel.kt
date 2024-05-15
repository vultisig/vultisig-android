package com.vultisig.wallet.presenter.settings.settings_main

import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.AppLanguage

data class SettingsUiModel(
    val selectedCurrency: CurrencyUnit = CurrencyUnit(),
    val selectedLocal: Language = Language()
)



data class CurrencyUnit(
    val name: String = "",
)
internal fun AppCurrency.toUiModel() = CurrencyUnit(name)

data class Language(
    val mainName: String = "",
    val englishName: String? = null
)

internal fun AppLanguage.toUiModel() = Language(mainName,engName)
