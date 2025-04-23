package com.vultisig.wallet.ui.utils

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

internal fun SemanticsNodeInteractionsProvider.click(tag: String) {
    onNodeWithTag(tag)
        .assertExists()
        .assertHasClickAction()
        .performClick()
}