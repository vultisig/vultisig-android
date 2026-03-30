package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.PasswordCheckResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
internal sealed class VerifyExistingVaultPasswordUiState {
    data object Loading : VerifyExistingVaultPasswordUiState()

    @Immutable
    data class Ready(val isPasswordVisible: Boolean = false, val error: UiText? = null) :
        VerifyExistingVaultPasswordUiState()
}

@HiltViewModel
internal class VerifyExistingVaultPasswordViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VaultInfo.VerifyExistingPassword>()
    private val vaultId = args.vaultId

    val passwordFieldState = TextFieldState()
    private val _state =
        MutableStateFlow<VerifyExistingVaultPasswordUiState>(
            VerifyExistingVaultPasswordUiState.Ready()
        )
    val state: StateFlow<VerifyExistingVaultPasswordUiState> = _state.asStateFlow()

    fun togglePasswordVisibility() {
        _state.update { current ->
            if (current is VerifyExistingVaultPasswordUiState.Ready)
                current.copy(isPasswordVisible = !current.isPasswordVisible)
            else current
        }
    }

    fun verify() {
        val password = passwordFieldState.text.toString()
        if (password.isBlank()) return

        val isPasswordVisible =
            (_state.value as? VerifyExistingVaultPasswordUiState.Ready)?.isPasswordVisible ?: false

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to verify vault password")
                _state.update {
                    VerifyExistingVaultPasswordUiState.Ready(
                        isPasswordVisible = isPasswordVisible,
                        error = UiText.DynamicString(e.message.orEmpty()),
                    )
                }
            }
        ) {
            _state.update { VerifyExistingVaultPasswordUiState.Loading }

            val vault = vaultRepository.get(vaultId)
            if (vault == null) {
                _state.update {
                    VerifyExistingVaultPasswordUiState.Ready(
                        isPasswordVisible = isPasswordVisible,
                        error = UiText.StringResource(R.string.push_notification_vault_not_found),
                    )
                }
                return@safeLaunch
            }

            when (val result = vultiSignerRepository.checkPassword(vault.pubKeyECDSA, password)) {
                is PasswordCheckResult.Valid -> {
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            vaultName = args.name,
                            email = args.email,
                            action = args.tssAction,
                            vaultId = vaultId,
                            password = password,
                        )
                    )
                }
                is PasswordCheckResult.Invalid -> {
                    _state.update {
                        VerifyExistingVaultPasswordUiState.Ready(
                            isPasswordVisible = isPasswordVisible,
                            error = UiText.StringResource(R.string.fast_vault_invalid_password),
                        )
                    }
                }
                is PasswordCheckResult.NetworkError -> {
                    _state.update {
                        VerifyExistingVaultPasswordUiState.Ready(
                            isPasswordVisible = isPasswordVisible,
                            error = UiText.StringResource(R.string.network_connection_lost),
                        )
                    }
                }
                is PasswordCheckResult.Error -> {
                    _state.update {
                        VerifyExistingVaultPasswordUiState.Ready(
                            isPasswordVisible = isPasswordVisible,
                            error = UiText.DynamicString(result.message),
                        )
                    }
                }
            }
        }
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }
}
