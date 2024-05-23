package com.vultisig.wallet.ui.screens.vault_settings

internal data class VaultSettingsState(
    val id: String = "",
    val checkedCautionIndexes: List<Int> = emptyList(),
    val cautionsBeforeDelete: List<Int> = emptyList(),
    val showDeleteConfirmScreen: Boolean = false,
    val isDeleteButtonEnabled: Boolean = false,
)