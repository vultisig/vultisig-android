package com.vultisig.wallet.ui.pages.onboarding

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

class StartPage(
    private val rule: SemanticsNodeInteractionsProvider,
) {

    fun createNewVault() {
        rule.onNodeWithTag("StartScreen.createNewVault")
            .performClick()
    }

}