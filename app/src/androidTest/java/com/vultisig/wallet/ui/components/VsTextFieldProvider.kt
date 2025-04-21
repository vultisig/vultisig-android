package com.vultisig.wallet.ui.components

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import com.vultisig.wallet.ui.components.inputs.TEXT_INPUT_FIELD_TAG

internal fun SemanticsNodeInteractionsProvider.textField(tag: String) =
    onNodeWithTag(tag)
        .onChildren()
        .filterToOne(hasTestTag(TEXT_INPUT_FIELD_TAG))