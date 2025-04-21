package com.vultisig.wallet.ui.pages.onboarding

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class OnboardingPage(
    private val rule: ComposeTestRule,
) {

    fun waitUntilShown() {
        rule.waitUntilShown("OnboardingScreen.skip")
    }

    fun skip() {
        rule.click("OnboardingScreen.skip")
    }

}