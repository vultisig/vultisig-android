package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.SystemNotificationStatus
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.pushNotificationErrorUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class VaultNotificationUiModel(
    val vaultId: String,
    val vaultName: String,
    val isEnabled: Boolean,
    val isFastVault: Boolean = false,
)

internal data class NotificationsSettingsUiState(
    val masterEnabled: Boolean = false,
    val vaults: List<VaultNotificationUiModel> = emptyList(),
    /**
     * Opted in in-app, but the OS will drop every push — permission revoked or the keysign channel
     * muted after opt-in. Neither raises a callback, so without this the toggle reads ON while
     * nothing is delivered.
     */
    val isBlockedBySystem: Boolean = false,
)

@HiltViewModel
internal class NotificationsSettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val pushNotificationManager: PushNotificationManager,
    private val systemNotificationStatus: SystemNotificationStatus,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsSettingsUiState())
    val state = _state.asStateFlow()

    private val pendingAction = AtomicReference<PendingAction>(PendingAction.AllVaults)

    private val _requestNotificationPermission = Channel<Unit>(Channel.BUFFERED)
    val requestNotificationPermission = _requestNotificationPermission.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(pushNotificationManager.observeAllSettings(), vaultRepository.getAllAsFlow()) {
                    settingsList,
                    allVaults ->
                    val vaults = allVaults.filter { it.isSecureVault() }
                    val settingsMap = settingsList.associateBy { it.vaultId }
                    val masterEnabled = settingsList.any { it.notificationsEnabled }

                    val vaultUiModels =
                        vaults.map { vault ->
                            VaultNotificationUiModel(
                                vaultId = vault.id,
                                vaultName = vault.name,
                                isEnabled = settingsMap[vault.id]?.notificationsEnabled == true,
                                isFastVault = vault.isFastVault(),
                            )
                        }

                    masterEnabled to vaultUiModels
                }
                .collect { (masterEnabled, vaultUiModels) ->
                    // Preserve isBlockedBySystem: it comes from the OS on resume, not from this
                    // flow, and replacing the whole state here would clear the warning.
                    _state.update { it.copy(masterEnabled = masterEnabled, vaults = vaultUiModels) }
                }
        }
    }

    fun onMasterToggle(enabled: Boolean) {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.w(e, "Failed to opt out all vaults from notifications")
                snackbarFlow.showMessage(pushNotificationErrorUiText(e), SnackbarType.Error)
            }
        ) {
            if (enabled) {
                pendingAction.set(PendingAction.AllVaults)
                _requestNotificationPermission.send(Unit)
            } else {
                pushNotificationManager.setAllVaultsOptIn(enabled = false)
            }
        }
    }

    fun onVaultToggle(vaultId: String, enabled: Boolean) {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.w(e, "Failed to opt out vault $vaultId from notifications")
                snackbarFlow.showMessage(pushNotificationErrorUiText(e), SnackbarType.Error)
            }
        ) {
            if (enabled) {
                pendingAction.set(PendingAction.SingleVault(vaultId))
                _requestNotificationPermission.send(Unit)
            } else {
                pushNotificationManager.setVaultOptIn(vaultId, enabled = false)
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) return
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.w(e, "Failed to opt in vault(s) for notifications")
                snackbarFlow.showMessage(pushNotificationErrorUiText(e), SnackbarType.Error)
            }
        ) {
            when (val pending = pendingAction.getAndSet(PendingAction.AllVaults)) {
                PendingAction.AllVaults -> pushNotificationManager.setAllVaultsOptIn(enabled = true)
                is PendingAction.SingleVault ->
                    pushNotificationManager.setVaultOptIn(pending.vaultId, enabled = true)
            }
        }
    }

    /**
     * Re-reads the OS notification state. Called on every resume because the user can revoke
     * permission or mute the channel from system settings while this screen sits in the background,
     * and neither change notifies the app.
     */
    fun refreshSystemNotificationState() {
        val blocked = !systemNotificationStatus.areNotificationsEnabled()
        _state.update { it.copy(isBlockedBySystem = blocked) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}

private sealed interface PendingAction {
    data object AllVaults : PendingAction

    data class SingleVault(val vaultId: String) : PendingAction
}
