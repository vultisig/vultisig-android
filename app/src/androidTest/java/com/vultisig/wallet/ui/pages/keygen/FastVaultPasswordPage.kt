package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vultisig.wallet.ui.components.textField
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class FastVaultPasswordPage(
    private val rule: ComposeTestRule,
) {

    fun waitUntilShown() {
        rule.waitUntilShown("FastVaultPasswordScreen.passwordField")
    }

    fun inputPassword(password: String) {
        rule.textField("FastVaultPasswordScreen.passwordField")
            .performTextInput(password)
    }

    fun inputConfirmPassword(password: String) {
        rule.textField("FastVaultPasswordScreen.confirmPasswordField")
            .performTextInput(password)
    }

    fun next() {
        rule.onNodeWithTag("FastVaultPasswordScreen.next")
            .performClick()
    }

}