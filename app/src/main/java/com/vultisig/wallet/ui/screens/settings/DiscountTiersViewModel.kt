package com.vultisig.wallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DiscountTiersViewModel @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository
) : ViewModel() {

    private val _currency = MutableStateFlow(AppCurrency.USD)
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    init {
        loadCurrency()
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect { currency ->
                _currency.value = currency
            }
        }
    }
}