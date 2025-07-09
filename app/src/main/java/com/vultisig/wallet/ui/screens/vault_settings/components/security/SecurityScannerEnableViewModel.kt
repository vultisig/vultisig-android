package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.OnChainSecurityScannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class SecurityScannerEnableUiModel(
    val isSwitchEnabled: Boolean = true,
    val showWarningDialog: Boolean = false,
)

@HiltViewModel
internal class SecurityScannerEnableViewModel @Inject constructor(
    private val onChainSecurityScannerRepository: OnChainSecurityScannerRepository,
): ViewModel() {
    val uiModel = MutableStateFlow(SecurityScannerEnableUiModel())

    init {
        initSwitchState()
    }

    private fun initSwitchState() {
        viewModelScope.launch {
            val switchEnabled = withContext(Dispatchers.IO) {
                onChainSecurityScannerRepository.getSecurityScannerStatus()
            }
            uiModel.update { it.copy(isSwitchEnabled = switchEnabled) }
        }
    }

    fun onCheckedChange(status: Boolean) {
        viewModelScope.launch {
            //if (!status) {
                uiModel.update { it.copy(isSwitchEnabled = status) }
            //}
        }
    }
}