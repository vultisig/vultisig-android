package com.vultisig.wallet.presenter.settings.language_setting

sealed class LanguageSettingEvent{
    data class ChangeLanguage(val selectedLanguage: LanguageItem): LanguageSettingEvent()
    data object InitSelectedLanguage: LanguageSettingEvent()
}