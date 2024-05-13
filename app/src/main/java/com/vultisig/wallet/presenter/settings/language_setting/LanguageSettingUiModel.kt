package com.vultisig.wallet.presenter.settings.language_setting


data class LanguageSettingUiModel(
    val languages: List<LanguageItem> = listOf(),
    val selectedLanguageId: Int = 0,
)

data class LanguageItem(
    val id: Int = 0,
    val name: String = "",
    val englishName: String? = null
)