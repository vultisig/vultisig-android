package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class StartScreenViewModel @Inject constructor(
    private val navigator: Navigator<Destination>
) : ViewModel(){
    val isAnimationRunning = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            delay(50) // To avoid losing false values
            isAnimationRunning.value = true
        }
    }

    fun navigateToCreateVault(){
        viewModelScope.launch {
            navigator.navigate(Destination.SelectVaultType)
        }
    }

    fun navigateToScanQrCode(){
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

    fun navigateToImportVault(){
        viewModelScope.launch {
            navigator.navigate(Destination.ImportVault)
        }
    }

}