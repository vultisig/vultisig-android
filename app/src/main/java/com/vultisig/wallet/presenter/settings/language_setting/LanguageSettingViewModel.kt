package com.vultisig.wallet.presenter.settings.language_setting

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.data.models.AppLanguage.Companion.fromName
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.presenter.settings.settings_main.Language
import com.vultisig.wallet.presenter.settings.settings_main.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LanguageSettingViewModel @Inject constructor(
    private val appLocaleRepository: AppLocaleRepository,
    @ApplicationContext private val context: Context,
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
        changeAppLanguage(language.mainName.fromName().toString())
        viewModelScope.launch {
            appLocaleRepository.setLocale(language)
        }
    }

    private fun changeAppLanguage(locale:String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(locale)
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(locale)
            )
        }
    }

}