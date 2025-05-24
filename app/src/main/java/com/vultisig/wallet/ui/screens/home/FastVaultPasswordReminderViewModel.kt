package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

internal data class FastVaultPasswordReminderUiModel(
    val isPasswordVisible: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
internal class FastVaultPasswordReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.FastVaultPasswordReminder>()

    val passwordFieldState = TextFieldState()

    val state = MutableStateFlow(FastVaultPasswordReminderUiModel())

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    fun togglePasswordVisibility() {
        state.update {
            it.copy(isPasswordVisible = !it.isPasswordVisible)
        }
    }

    fun verify() {
        val password = passwordFieldState.text.toString()

        viewModelScope.launch {
            val vaultId = args.vaultId
            val vault = vaultRepository.get(vaultId)
                ?: return@launch

            if (vultiSignerRepository.isPasswordValid(vault.pubKeyECDSA, password)) {
                vaultMetadataRepo.setFastVaultPasswordReminderShownDate(
                    vaultId = vaultId,
                    date = Clock.System.todayIn(
                        TimeZone.currentSystemDefault()
                    )
                )

                back()
            } else {
                state.update {
                    it.copy(error = UiText.DynamicString("Invalid password"))
                }
            }
        }
    }

}