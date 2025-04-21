package com.vultisig.wallet.ui.pages.onboarding

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SummaryPage(
    private val compose: ComposeTestRule,
) {

    fun waitUntilShown(timeout: Duration = 1.seconds) {
        compose.waitUntilShown("SummaryScreen.agree", timeout)
    }

    fun toggleAgreement() {
        compose.click("SummaryScreen.agree")
    }

    fun next() {
        compose.click("SummaryScreen.continue")
    }

}