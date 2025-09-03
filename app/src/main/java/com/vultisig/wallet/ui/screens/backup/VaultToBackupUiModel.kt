package com.vultisig.wallet.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val remainedCount: Int? = null,
)


@HiltViewModel
internal class VaultsToBackupViewModel @Inject constructor(
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val uiState = MutableStateFlow(
        BackupVaultUiModel()
    )


    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}