package com.vultisig.wallet.ui.pages.onboarding

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.vultisig.wallet.ui.components.onboarding.OnboardingContentTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VaultBackupOnboardingPage(
    private val compose: ComposeTestRule,
) {

    fun waitUntilShown(timeout: Duration) {
        compose.waitUntilShown(OnboardingContentTags.NEXT, timeout)
    }

    fun skipFastVault() {
        // skip through vault backup onboarding
        val nextTimeout = 3.seconds
        compose.click(OnboardingContentTags.NEXT)
        compose.waitUntilShown(OnboardingContentTags.NEXT, nextTimeout)
        compose.click(OnboardingContentTags.NEXT)
        compose.waitUntilShown(OnboardingContentTags.NEXT, nextTimeout)
        compose.click(OnboardingContentTags.NEXT)
    }

    fun skipMigration() {
        compose.click(OnboardingContentTags.NEXT)
    }

}