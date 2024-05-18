package com.vultisig.wallet.ui.screens.vault_settings

data class VaultSettingsState(
    val checkedCautionIndexes: List<Int> = emptyList(),
    val cautionsBeforeDelete: List<Int> = emptyList(),
    val showDeleteConfirmScreen: Boolean = false,
    val isDeleteButtonEnabled: Boolean = false,
)