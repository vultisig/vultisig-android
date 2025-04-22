package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vultisig.wallet.ui.components.textField
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class FastVaultEmailPage(
    private val rule: ComposeTestRule,
) {

    fun waitUntilShown() {
        rule.waitUntilShown("FastVaultEmailScreen.emailField")
    }

    fun inputEmail(email: String) {
        rule.textField("FastVaultEmailScreen.emailField")
            .performTextInput(email)
    }

    fun next() {
        rule.onNodeWithTag("FastVaultEmailScreen.next")
            .performClick()
    }

}


