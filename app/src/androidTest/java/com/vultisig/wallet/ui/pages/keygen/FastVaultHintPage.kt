package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class FastVaultHintPage(
    private val rule: ComposeTestRule,
) {

    fun waitUntilShown() {
        rule.waitUntilShown("FastVaultPasswordHintScreen.skip")
    }

    fun skip() {
        rule.click("FastVaultPasswordHintScreen.skip")
    }

}