@file:OptIn(ExperimentalTestApi::class)

package com.vultisig.wallet.ui.pages.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class SelectChainsPage(
    private val compose: ComposeTestRule
) {

    fun waitUntilShown() {
        compose.waitUntilAtLeastOneExists(hasText("Chains"))
    }

    fun toggleChain(chain: String) {
        compose.onNodeWithText(chain)
            .performClick()
    }

}