package com.vultisig.wallet.ui.screens.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultToBackupUiModel(
    val name: String = "",
    val part: Int = 0,
    val size: Int = 0,
    val isFast: Boolean = false,
)

internal data class BackupVaultUiModel(
    val currentVault: VaultToBackupUiModel = VaultToBackupUiModel(),
    val vaultsToBackup: List<VaultToBackupUiModel> = emptyList(),
)


@HiltViewModel
internal class VaultsToBackupViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.VaultsToBackup>().vaultId

    init {
        loadData()
    }

    val uiState = MutableStateFlow(
        BackupVaultUiModel()
    )

    private fun loadData() {
        viewModelScope.launch {
            val currentVault = vaultRepository.get(vaultId)?.toUi()
                ?: error("Vault not found")
            val allVaults = vaultRepository.getAll().map {
                it.toUi()
            }
            uiState.update {
                it.copy(
                    currentVault = currentVault,
                    vaultsToBackup = allVaults,
                )
            }
        }
    }


    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    fun backupCurrentVault() {
        viewModelScope.launch {
            navigator.route(
                Route.BackupPasswordRequest(
                    vaultId = vaultId,
                    backupType = BackupType.CurrentVault(),
                )
            )
        }
    }

    fun backupAllVaults() {
        viewModelScope.launch {
            navigator.route(Route.BackupPasswordRequest(
                vaultId = vaultId,
                backupType = BackupType.AllVaults
            ))
        }
    }

    private fun Vault.toUi() = VaultToBackupUiModel(
        name = name,
        part = getVaultPart(),
        size = signers.size,
        isFast = isFastVault(),
    )

}