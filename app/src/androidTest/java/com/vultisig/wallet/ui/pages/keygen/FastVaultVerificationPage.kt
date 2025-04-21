package com.vultisig.wallet.ui.pages.keygen

import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.vultisig.wallet.ui.components.inputs.CODE_INPUT_FIELD_TAG
import com.vultisig.wallet.ui.utils.waitUntilShown

class FastVaultVerificationPage(
    private val compose: ComposeTestRule,
) {

    fun waitUntilShown() {
        compose.waitUntilShown("FastVaultVerificationScreen.codeField")
    }

    fun inputCode(code: String) {
        compose.onNodeWithTag("FastVaultVerificationScreen.codeField")
            .onChildren()
            .filterToOne(hasTestTag(CODE_INPUT_FIELD_TAG))
            .performTextInput(code)
    }

}