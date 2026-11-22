package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.BackupPasswordTypeNavType
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.VaultInfo
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class BackupVaultViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val args =
        savedStateHandle.toRoute<Route.BackupVault>(
            mapOf(typeOf<BackupPasswordType>() to BackupPasswordTypeNavType)
        )

    val isFastVault: Boolean = args.vaultType == VaultInfo.VaultType.Fast

    // Kept as a backing property so lint can see the string resource reference.
    private val _title =
        MutableStateFlow<UiText>(
            if (isFastVault) UiText.StringResource(R.string.backup_save_backup_to_the_cloud)
            else UiText.Empty
        )
    val title: StateFlow<UiText> = _title.asStateFlow()

    init {
        if (!isFastVault) {
            viewModelScope.launch {
                val vault = vaultRepository.get(args.vaultId) ?: return@launch
                val total = vault.signers.size
                val position = vault.getVaultPart().coerceAtLeast(1)
                _title.value =
                    UiText.FormattedText(
                        R.string.vault_setup_save_backup_n_of_n_to_the_cloud,
                        listOf(position, total),
                    )
            }
        }
    }

    fun backup() {
        viewModelScope.launch {
            val backupType =
                BackupType.CurrentVault(vaultType = args.vaultType, action = args.action)
            navigator.route(
                if (isFastVault) {
                    Route.BackupPassword(vaultId = args.vaultId, backupType = backupType)
                } else {
                    Route.BackupPasswordRequest(vaultId = args.vaultId, backupType = backupType)
                }
            )
        }
    }
}
