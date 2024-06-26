package com.vultisig.wallet.presenter.settings.language_setting

import com.vultisig.wallet.presenter.settings.settings_main.Language


data class LanguageSettingUiModel(
    val languages: List<Language> = listOf(),
    val selectedLanguage: Language = Language(),
    var showLanguagePrompt:Boolean= false
)


