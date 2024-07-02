package com.vultisig.wallet.ui.components.biometricAuth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val allowedAuthenticatorTypes
    get() = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

@HiltViewModel
internal class BiometryAuthViewModel @Inject constructor
    (@ApplicationContext private val context: Context) : ViewModel() {

    var isAuthorized by mutableStateOf(false)

    private val requestAuthenticationChannel = Channel<Unit>()
    val requestAuthenticationFlow = requestAuthenticationChannel.receiveAsFlow()

    init {
        showAuthPrompt()
    }

    fun canAuthenticateBiometric(): Boolean = BiometricManager.from(context)
        .canAuthenticate(allowedAuthenticatorTypes) == BIOMETRIC_SUCCESS

    fun showAuthPrompt() {
        viewModelScope.launch {
            requestAuthenticationChannel.send(Unit)
        }
    }

    fun hideAuthScreen() {
        isAuthorized = true
    }

}
