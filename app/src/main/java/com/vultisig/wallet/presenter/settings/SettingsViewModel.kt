package com.vultisig.wallet.presenter.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(private val navigator: Navigator<Destination>) :
    ViewModel() {
    fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }
}