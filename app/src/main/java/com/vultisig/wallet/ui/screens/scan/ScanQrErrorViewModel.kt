package com.vultisig.wallet.ui.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ScanQrErrorViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}