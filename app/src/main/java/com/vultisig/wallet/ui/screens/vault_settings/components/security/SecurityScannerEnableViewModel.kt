package com.vultisig.wallet.ui.screens.vault_settings.components.security

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow

internal data class SecurityScannerEnableUiModel(
    val isSwitchEnabled: Boolean = true,
)

@HiltViewModel
internal class SecurityScannerEnableViewModel: ViewModel() {

    val uiModel = MutableStateFlow(SecurityScannerEnableUiModel())

    init {
        initSwitchState()
    }

    private fun initSwitchState() {

    }

    fun onCheckedChange(status: Boolean) {

    }
}