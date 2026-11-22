package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTextInput
import com.vultisig.wallet.ui.components.textField
import com.vultisig.wallet.ui.screens.keygen.BackupVaultScreenTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

class BackupVaultPage(private val compose: ComposeTestRule) {

    fun waitUntilShown() {
        compose.waitUntilShown(BackupVaultScreenTags.BACKUP_NOW)
    }

    fun backupNow() {
        compose.click("SummaryScreen.agree")
        compose.click(BackupVaultScreenTags.BACKUP_NOW)
    }

    fun exportWithLocalPassphrase(passphrase: String) {
        compose.waitUntilShown("BackupPasswordScreen.passwordField")
        compose.textField("BackupPasswordScreen.passwordField").performTextInput(passphrase)
        compose.textField("BackupPasswordScreen.confirmPasswordField").performTextInput(passphrase)
        compose.click("BackupPasswordScreen.backUp")
    }
}
