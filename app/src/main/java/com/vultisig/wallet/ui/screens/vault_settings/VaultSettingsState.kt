package com.vultisig.wallet.ui.screens.vault_settings

internal data class VaultSettingsState(
    val hasReshare: Boolean = false,
    val hasFastSign: Boolean = false,
    val hasMigration: Boolean = false,
)