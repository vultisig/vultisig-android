package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.utils.TextFieldUtils.HINT_MAX_LENGTH
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.NavigationSecrets
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FastVaultPasswordHintUiModel(
    val isNextAvailable: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
internal class FastVaultPasswordHintViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val navigator: Navigator<Destination>) :
    ViewModel() {

    val state = MutableStateFlow(FastVaultPasswordHintUiModel())
    val passwordHintFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.VaultInfo.PasswordHint>()
    private var password = NavigationSecrets.take(args.credentialId)

    init {
        viewModelScope.launch {
            passwordHintFieldState.textAsFlow().collect { validateHint(it.toString()) }
        }
    }

    fun next() {
        val hint = passwordHintFieldState.text.toString()
        if (isHintValid(hint)) {
            openPeerDiscovery(hint = hint)
        }
    }

    fun skip() {
        openPeerDiscovery(hint = null)
    }

    private fun openPeerDiscovery(hint: String?) {
        viewModelScope.launch {
            val password = password
            if (password == null) {
                navigator.route(
                    Route.VaultInfo.Password(args.name, args.email, args.tssAction, args.vaultId),
                    NavigationOptions(popUpToRoute = Route.VaultInfo.PasswordHint::class, inclusive = true),
                )
                return@launch
            }
            navigator.route(
                Route.Keygen.PeerDiscovery(
                    action = args.tssAction,
                    vaultName = args.name,
                    email = args.email,
                    credentialId = NavigationSecrets.handoff(password),
                    hint = hint,
                    vaultId = args.vaultId,
                )
            )
            this@FastVaultPasswordHintViewModel.password = null
        }
    }

    private fun validateHint(hint: String) {
        val isNextAvailable = hint.isNotEmpty()

        val errorMessage =
            if (!isHintValid(hint)) UiText.StringResource(R.string.vault_password_hint_too_long)
            else null

        state.update { it.copy(isNextAvailable = isNextAvailable, errorMessage = errorMessage) }
    }

    private fun isHintValid(hint: String) = hint.length <= HINT_MAX_LENGTH

    override fun onCleared() {
        password = null
        super.onCleared()
    }

    fun back() {
        password = null
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }
}
