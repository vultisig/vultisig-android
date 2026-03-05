package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.notifications.PushNotificationManager
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class VaultNotificationUiModel(
    val vaultId: String,
    val vaultName: String,
    val isEnabled: Boolean,
)

internal data class NotificationsSettingsUiState(
    val masterEnabled: Boolean = false,
    val vaults: List<VaultNotificationUiModel> = emptyList(),
)

@HiltViewModel
internal class NotificationsSettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val pushNotificationManager: PushNotificationManager,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsSettingsUiState())
    val state = _state

    init {
        viewModelScope.launch {
            val allVaults = vaultRepository.getAll().filter { it.isFastVault().not() }

            combine(pushNotificationManager.observeAllSettings(), flowOf(allVaults)) {
                    settingsList,
                    vaults ->
                    val settingsMap = settingsList.associateBy { it.vaultId }
                    val masterEnabled = settingsList.any { it.notificationsEnabled }

                    val vaultUiModels =
                        vaults.map { vault ->
                            VaultNotificationUiModel(
                                vaultId = vault.id,
                                vaultName = vault.name,
                                isEnabled = settingsMap[vault.id]?.notificationsEnabled == true,
                            )
                        }

                    NotificationsSettingsUiState(
                        masterEnabled = masterEnabled,
                        vaults = vaultUiModels,
                    )
                }
                .collect { newState -> _state.update { newState } }
        }
    }

    fun onMasterToggle(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val allVaults = vaultRepository.getAll()
                pushNotificationManager.setAllVaultsOptIn(allVaults, enabled = true)
            } else {
                val allVaults = vaultRepository.getAll()
                pushNotificationManager.setAllVaultsOptIn(allVaults, enabled = false)
            }
        }
    }

    fun onVaultToggle(vaultId: String, enabled: Boolean) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            pushNotificationManager.setVaultOptIn(vault, enabled)
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
