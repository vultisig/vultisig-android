package com.vultisig.wallet.ui.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTextInput
import com.vultisig.wallet.ui.components.textField
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class NameVaultPage(
    private val rule: ComposeTestRule
) {

    fun waitUntilShown() {
        rule.waitUntilShown("NameVaultScreen.nameField")
    }

    fun inputName(name: String) {
        rule.textField("NameVaultScreen.nameField")
            .performTextInput(name)
    }

    fun next() {
        rule.click("NameVaultScreen.continue")
    }

}


