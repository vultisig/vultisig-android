package com.vultisig.wallet.presenter.settings.language_setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LanguageSettingViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val selectedLanguageId = savedStateHandle.get<Int>(Destination.LanguageSetting.ARG_LANG_ID)?:0
    val state = MutableStateFlow(LanguageSettingUiModel())

    init {
        state.update {
            it.copy(
                languages = listOf(
                    LanguageItem(id = 0,"English (UK)", null),
                    LanguageItem(id = 1,"Deutsch", "German"),
                    LanguageItem(id = 2,"Espanol", "Spanish"),
                    LanguageItem(id = 3,"Italiano", "Italian"),
                    LanguageItem(id = 4,"Hrvatski", "Croatian"),
                ),
                selectedLanguageId = 0
            )
        }
    }

    fun onEvent(event: LanguageSettingEvent) {
        when (event) {
            is LanguageSettingEvent.ChangeLanguage -> changeLanguage(event.selectedLanguage)
            LanguageSettingEvent.InitSelectedLanguage -> initSelectedLanguage()
        }
    }

    private fun initSelectedLanguage() {
        state.update {
            it.copy(selectedLanguageId = selectedLanguageId)
        }
    }

    private fun changeLanguage(language: LanguageItem) {
        state.update {
            it.copy(selectedLanguageId = language.id)
        }
    }

}