package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.screens.keygen.BackupVaultScreenTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

class BackupVaultPage(
    private val compose: ComposeTestRule,
) {

    fun waitUntilShown() {
        compose.waitUntilShown(BackupVaultScreenTags.BACKUP_NOW)
    }

    fun backupNow() {
        compose.click(BackupVaultScreenTags.BACKUP_NOW)
    }

}