package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
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
    val state = _state.asStateFlow()

    // null = all vaults, non-null = specific vault
    private var pendingVaultId: String? = PENDING_ALL_VAULTS

    private val _requestNotificationPermission = Channel<Unit>(Channel.BUFFERED)
    val requestNotificationPermission = _requestNotificationPermission.receiveAsFlow()

    init {
        viewModelScope.launch {
            val allVaults = vaultRepository.getAll().filter { it.isSecureVault() }

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
                pendingVaultId = PENDING_ALL_VAULTS
                _requestNotificationPermission.send(Unit)
            } else {
                pushNotificationManager.setAllVaultsOptIn(enabled = false)
            }
        }
    }

    fun onVaultToggle(vaultId: String, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                pendingVaultId = vaultId
                _requestNotificationPermission.send(Unit)
            } else {
                pushNotificationManager.setVaultOptIn(vaultId, enabled = false)
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            val pending = pendingVaultId
            if (pending == PENDING_ALL_VAULTS) {
                pushNotificationManager.setAllVaultsOptIn(enabled = true)
            } else if (pending != null) {
                pushNotificationManager.setVaultOptIn(pending, enabled = true)
            }
            pendingVaultId = PENDING_ALL_VAULTS
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    private companion object {
        const val PENDING_ALL_VAULTS = ""
    }
}
