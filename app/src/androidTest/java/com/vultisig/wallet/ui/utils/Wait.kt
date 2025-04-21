package com.vultisig.wallet.ui.utils

import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


internal fun ComposeTestRule.waitUntilShown(
    tag: String,
    timeout: Duration = 1.seconds,
) {
    waitUntil(timeoutMillis = timeout.inWholeMilliseconds) {
        onNodeWithTag(tag)
            .isDisplayed()
    }
}