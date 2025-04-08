package com.vultisig.wallet.ui.models.keysign

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class KeysignPasswordUiModel(
    val isPasswordVisible: Boolean = false,
    val passwordError: UiText? = null,
    val passwordHint: UiText? = null,
)

@HiltViewModel
internal class KeysignPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Keysign.Password>()
    private val transactionId: TransactionId = args.transactionId

    val state = MutableStateFlow(KeysignPasswordUiModel())

    val passwordFieldState = TextFieldState()

    private val password: String
        get() = passwordFieldState.text.toString()

    private fun verifyPassword() {
        val error = if (isPasswordEmpty()) {
            UiText.StringResource(R.string.password_should_not_be_empty)
        } else null
        state.update {
            it.copy(passwordError = error)
        }
    }

    fun togglePasswordVisibility() {
        state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun proceed() {
        val password = password
        if (!isPasswordEmpty()) {
            viewModelScope.launch {
                val vaultId = args.vaultId
                val vault = vaultRepository.get(vaultId)
                    ?: error("No vault with id $vaultId exists")

                val isPasswordValid = vultiSignerRepository.isPasswordValid(
                    publicKeyEcdsa = vault.pubKeyECDSA,
                    password = password,
                )

                if (isPasswordValid) {
                    navigator.route(
                        Route.Keysign.Keysign(
                            transactionId = transactionId,
                            password = password,
                            txType = args.txType,
                        )
                    )
                } else {
                    val passwordHint = getPasswordHint(vaultId)
                    state.update {
                        it.copy(
                            passwordError = UiText.StringResource(
                                R.string.keysign_password_incorrect_password
                            ),
                            passwordHint = passwordHint
                        )
                    }
                }
            }
        } else {
            verifyPassword()
        }
    }

    private suspend fun getPasswordHint(vaultId: String): UiText? {

        val passwordHintString =
            vaultDataStoreRepository.readFastSignHint(vaultId = vaultId).first()

        if (passwordHintString.isEmpty()) return null

        return UiText.FormattedText(
            R.string.import_file_password_hint_text,
            listOf(passwordHintString)
        )
    }

    private fun isPasswordEmpty() = password.isEmpty()

}