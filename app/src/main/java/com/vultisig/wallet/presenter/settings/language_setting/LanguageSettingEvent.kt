package com.vultisig.wallet.presenter.settings.language_setting

import com.vultisig.wallet.presenter.settings.settings_main.Language

sealed class LanguageSettingEvent{
    data class ChangeLanguage(val selectedLanguage: Language): LanguageSettingEvent()
    data object InitSelectedLanguage: LanguageSettingEvent()
}