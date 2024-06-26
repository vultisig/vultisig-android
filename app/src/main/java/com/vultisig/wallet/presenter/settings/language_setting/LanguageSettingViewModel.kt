package com.vultisig.wallet.presenter.settings.language_setting

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.data.models.AppLanguage.Companion.fromName
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.presenter.settings.settings_main.Language
import com.vultisig.wallet.presenter.settings.settings_main.toUiModel
import com.vultisig.wallet.ui.components.UiDialog
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
        viewModelScope.launch {
            appLocaleRepository.setLocale(language)
        }
        state.update {
            it.copy(showLanguagePrompt = true)
        }
    }



    fun onDismissLanguagePrompt() {
        state.update {
            it.copy(showLanguagePrompt = false)
        }

    }
}