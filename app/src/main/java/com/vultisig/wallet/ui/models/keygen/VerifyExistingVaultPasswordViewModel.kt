package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.PasswordCheckResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class VerifyExistingVaultPasswordUiModel(
  val isPasswordVisible: Boolean = false,
  val isLoading: Boolean = false,
  val error: UiText? = null,
)

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

  val passwordFieldState = TextFieldState()
  val state = MutableStateFlow(VerifyExistingVaultPasswordUiModel())

  fun togglePasswordVisibility() {
    state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
  }

  fun verify() {
    val password = passwordFieldState.text.toString()
    if (password.isBlank()) return

    viewModelScope.launch {
      state.update { it.copy(isLoading = true, error = null) }

      val vault = vaultRepository.get(args.vaultId!!)
      if (vault == null) {
        state.update {
          it.copy(
            isLoading = false,
            error = UiText.StringResource(R.string.push_notification_vault_not_found),
          )
        }
        return@launch
      }

      val result = vultiSignerRepository.checkPassword(vault.pubKeyECDSA, password)

      when (result) {
        is PasswordCheckResult.Valid -> {
          navigator.route(
            Route.Keygen.PeerDiscovery(
              vaultName = args.name,
              email = args.email,
              action = args.tssAction,
              vaultId = args.vaultId,
              password = password,
            )
          )
        }
        is PasswordCheckResult.Invalid -> {
          state.update {
            it.copy(
              isLoading = false,
              error = UiText.StringResource(R.string.fast_vault_invalid_password),
            )
          }
        }
        is PasswordCheckResult.NetworkError -> {
          state.update {
            it.copy(
              isLoading = false,
              error = UiText.StringResource(R.string.network_connection_lost),
            )
          }
        }
        is PasswordCheckResult.Error -> {
          state.update { it.copy(isLoading = false, error = UiText.DynamicString(result.message)) }
        }
      }
    }
  }

  fun back() {
    viewModelScope.launch { navigator.navigate(Destination.Back) }
  }
}
