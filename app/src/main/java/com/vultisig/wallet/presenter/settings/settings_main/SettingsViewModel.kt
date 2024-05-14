package com.vultisig.wallet.presenter.settings.settings_main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject



@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val appLocaleRepository: AppLocaleRepository
) : ViewModel() {

    val state = MutableStateFlow(SettingsUiModel())
    fun loadSettings() {
        viewModelScope.launch {
            loadCurrency()
            loadAppLocale()
        }
    }

    private fun loadAppLocale() {
        viewModelScope.launch {
            appLocaleRepository.local.collect{ locale: AppLanguage ->
                state.update {
                    it.copy(selectedLocal = locale.toUiModel())
                }
            }
        }
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect{ currency: AppCurrency ->
                state.update {
                    it.copy(selectedCurrency = currency.toUiModel())
                }
            }
        }
    }

    fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }


}