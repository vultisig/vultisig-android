package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class ChooseVaultPage(
    private val rule: ComposeTestRule,
) {

    fun waitUntilShown() {
        rule.waitUntilShown("ChooseVaultScreen.selectFastVault")
    }

    fun selectFastVault() {
        rule.click("ChooseVaultScreen.selectFastVault")
    }

    fun next() {
        rule.click("ChooseVaultScreen.continue")
    }

}