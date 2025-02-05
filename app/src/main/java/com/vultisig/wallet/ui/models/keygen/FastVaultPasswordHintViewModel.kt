package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.utils.TextFieldUtils.HINT_MAX_LENGTH
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordHintUiModel(
    val errorMessage: UiText? = null,
)

@HiltViewModel
internal class FastVaultPasswordHintViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultPasswordHintUiModel())
    val passwordHintFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.VaultInfo.PasswordHint>()

    init {
        viewModelScope.launch {
            passwordHintFieldState.textAsFlow().collect {
                validateHint(it)
            }
        }
    }

    fun next() {
        openPeerDiscovery()
    }

    fun skip() {
        openPeerDiscovery()
    }

    private fun openPeerDiscovery() {
        // TODO save hint
        viewModelScope.launch {
            navigator.route(
                Route.Keygen.PeerDiscovery(
                    vaultName = args.name,
                    email = args.email,
                    password = args.password,
                )
            )
        }
    }

    private fun validateHint(hint: CharSequence) {
        val errorMessage =
            if (hint.length > HINT_MAX_LENGTH)
                UiText.StringResource(R.string.vault_password_hint_to_long)
            else null

        state.update {
            it.copy(errorMessage = errorMessage)
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}