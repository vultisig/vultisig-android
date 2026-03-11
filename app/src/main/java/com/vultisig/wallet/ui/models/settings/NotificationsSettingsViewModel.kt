package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.PushNotificationError
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.toStringRes
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
)

internal data class NotificationsSettingsUiState(
    val masterEnabled: Boolean = false,
    val vaults: List<VaultNotificationUiModel> = emptyList(),
)

@HiltViewModel
internal class NotificationsSettingsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val pushNotificationManager: PushNotificationManager,
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
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.w(e, "Failed to opt out all vaults from notifications")
                val msgRes =
                    (e as? PushNotificationError)?.toStringRes()
                        ?: R.string.push_notifications_failed
                snackbarFlow.showMessage(context.getString(msgRes), SnackbarType.Error)
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
                val msgRes =
                    (e as? PushNotificationError)?.toStringRes()
                        ?: R.string.push_notifications_failed
                snackbarFlow.showMessage(context.getString(msgRes), SnackbarType.Error)
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
                val msgRes =
                    (e as? PushNotificationError)?.toStringRes()
                        ?: R.string.push_notifications_failed
                snackbarFlow.showMessage(context.getString(msgRes), SnackbarType.Error)
            }
        ) {
            when (val pending = pendingAction.getAndSet(PendingAction.AllVaults)) {
                PendingAction.AllVaults -> pushNotificationManager.setAllVaultsOptIn(enabled = true)
                is PendingAction.SingleVault ->
                    pushNotificationManager.setVaultOptIn(pending.vaultId, enabled = true)
            }
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}

private sealed interface PendingAction {
    data object AllVaults : PendingAction

    data class SingleVault(val vaultId: String) : PendingAction
}
