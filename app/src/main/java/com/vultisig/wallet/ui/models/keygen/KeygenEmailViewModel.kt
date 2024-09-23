@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.util.Patterns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class KeygenEmailUiModel(
    val shouldReceiveAlerts: Boolean = true,
    val emailError: UiText? = null,
    val verifyEmailError: UiText? = null,
)

@HiltViewModel
internal class KeygenEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(KeygenEmailUiModel())

    val emailFieldState = TextFieldState()
    val verifyEmailFieldState = TextFieldState()

    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]
    private val name: String? = savedStateHandle.get<String>(Destination.ARG_VAULT_NAME)
    private val setupType = VaultSetupType.fromInt(
        requireNotNull(savedStateHandle.get<Int>(Destination.ARG_VAULT_SETUP_TYPE))
    )

    private val email: String
        get() = emailFieldState.text.toString()

    private val emailDouble: String
        get() = verifyEmailFieldState.text.toString()

    fun verifyEmail() {
        val error = if (!isEmailValid()) {
            UiText.StringResource(R.string.keygen_invalid_email_error)
        } else null
        state.update { it.copy(emailError = error) }
    }

    fun verifyEmailDouble() {
        val error = if (!isEmailsMatch()) {
            UiText.StringResource(R.string.keygen_email_dont_match)
        } else null

        state.update {
            it.copy(
                verifyEmailError = error
            )
        }
    }

    fun receiveAlerts(shouldReceiveAlerts: Boolean) {
        state.update { it.copy(shouldReceiveAlerts = shouldReceiveAlerts) }
    }

    fun proceed() {
        val email = email
        if (isEmailValid() && isEmailsMatch()) {
            viewModelScope.launch {
                navigator.navigate(
                    Destination.KeygenPassword(
                        vaultId = vaultId,
                        name = name,
                        setupType = setupType,
                        email = email,
                    )
                )
            }
        } else {
            verifyEmail()
            verifyEmailDouble()
        }
    }

    private fun isEmailValid(): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isEmailsMatch(): Boolean = email == emailDouble

}