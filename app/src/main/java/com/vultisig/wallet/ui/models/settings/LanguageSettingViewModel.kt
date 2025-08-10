package com.vultisig.wallet.ui.models.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Language
import com.vultisig.wallet.data.models.settings.AppLanguage
import com.vultisig.wallet.data.models.settings.AppLanguage.Companion.fromName
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class LanguageSettingUiModel(
    val languages: List<Language> = listOf(),
    val selectedLanguage: Language = Language(),
)


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


    fun initSelectedLanguage() {
        viewModelScope.launch {
            appLocaleRepository.local.collect { local ->
                state.update {
                    it.copy(selectedLanguage = local.toUiModel())
                }
            }
        }
    }

    fun changeLanguage(language: Language) {
        viewModelScope.launch {
            appLocaleRepository.setLocale(language)
            changeAppLanguage(language.mainName.fromName().toString())
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

    private fun AppLanguage.toUiModel() = Language(mainName,engName)

}