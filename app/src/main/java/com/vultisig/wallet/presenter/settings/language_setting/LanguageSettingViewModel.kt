package com.vultisig.wallet.presenter.settings.language_setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.presenter.settings.settings_main.Language
import com.vultisig.wallet.presenter.settings.settings_main.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LanguageSettingViewModel @Inject constructor(
    private val appLocaleRepository: AppLocaleRepository
) : ViewModel() {

    val state = MutableStateFlow(
        LanguageSettingUiModel(
            languages = appLocaleRepository.getAllLocales()
                .map { lang: AppLanguage -> lang.toUiModel() },
        )
    )


    fun onEvent(event: LanguageSettingEvent) {
        when (event) {
            is LanguageSettingEvent.ChangeLanguage -> changeLanguage(event.selectedLanguage)
            LanguageSettingEvent.InitSelectedLanguage -> initSelectedLanguage()
        }
    }

    private fun initSelectedLanguage() {
        viewModelScope.launch {
            appLocaleRepository.local.collect { local ->
                state.update {
                    it.copy(selectedLanguage = local.toUiModel())
                }
            }
        }
    }

    private fun changeLanguage(language: Language) {
        viewModelScope.launch {
            appLocaleRepository.setLocale(language)
        }
    }

}