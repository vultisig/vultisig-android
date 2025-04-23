@file:OptIn(ExperimentalTestApi::class)

package com.vultisig.wallet.ui.pages.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import com.vultisig.wallet.ui.screens.home.VaultAccountsScreenTags
import com.vultisig.wallet.ui.utils.click
import com.vultisig.wallet.ui.utils.waitUntilShown

internal class VaultAccountsPage(
    private val compose: ComposeTestRule
) {

    fun waitUntilShown() {
        compose.waitUntilShown("VaultAccountsScreen.chooseChains")
    }

    fun chooseChains() {
        compose.click("VaultAccountsScreen.chooseChains")
    }

    fun waitChain(chain: String) {
        compose.waitUntilAtLeastOneExists(hasText(chain))
    }

    fun assertNotExist(chain: String) {
        compose.onNodeWithText(chain)
            .assertDoesNotExist()
    }

    fun migrate() {
        compose.click(VaultAccountsScreenTags.MIGRATE)
    }

}


