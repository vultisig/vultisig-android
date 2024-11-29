package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BackupSuggestionUiModel(
    val ableToSkip: Boolean = true,
    val showSkipConfirm: Boolean = false,
    val isConsentChecked: Boolean = false,
)

@HiltViewModel
internal class BackupSuggestionViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))

    val uiModel = MutableStateFlow(BackupSuggestionUiModel())

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
            uiModel.update { it.copy(ableToSkip = vault?.isFastVault() == false) }
        }
    }

    fun close() {
        if (uiModel.value.ableToSkip) {
            uiModel.update { it.copy(showSkipConfirm = true) }
        }
    }

    fun closeSkipConfirm() {
        uiModel.update { it.copy(showSkipConfirm = false, isConsentChecked = false) }
    }

    fun checkConsent(check: Boolean) {
        uiModel.update { it.copy(isConsentChecked = check) }
    }

    fun skipBackup() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Home(
                    openVaultId = vaultId
                ),
                opts = NavigationOptions(clearBackStack = true)
            )
        }
    }

    fun navigateToBackupPasswordScreen() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.BackupPassword(vaultId)
            )
        }
    }
}