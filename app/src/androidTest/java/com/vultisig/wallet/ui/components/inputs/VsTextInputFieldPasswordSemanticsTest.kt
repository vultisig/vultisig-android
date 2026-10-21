package com.vultisig.wallet.ui.components.inputs

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * A secure field is masked on screen, but accessibility services and `uiautomator dump` read the
 * semantics tree, so the typed value must not reach it in either visibility mode.
 */
class VsTextInputFieldPasswordSemanticsTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aHiddenPasswordIsMaskedInTheSemanticsTree() {
        assertMasked(isVisible = false)
    }

    @Test
    fun aRevealedPasswordIsStillMaskedInTheSemanticsTree() {
        assertMasked(isVisible = true)
    }

    @Test
    fun thePasswordFlagSurvivesTheOverride() {
        start(isVisible = false)

        field().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    private fun assertMasked(isVisible: Boolean) {
        val state = start(isVisible)

        field().performTextInput(SECRET)
        compose.waitForIdle()

        assertEquals(SECRET, state.text.toString())
        val exposed =
            field().fetchSemanticsNode().config[SemanticsProperties.EditableText].toString()
        assertFalse(exposed.contains(SECRET))
        assertEquals(maskedPasswordText(SECRET.length).text, exposed)
    }

    private fun start(isVisible: Boolean): TextFieldState {
        val state = TextFieldState()
        compose.setContent {
            VsTextInputField(
                textFieldState = state,
                type = VsTextInputFieldType.Password(isVisible = isVisible) {},
            )
        }
        return state
    }

    private fun field() = compose.onNodeWithTag(TEXT_INPUT_FIELD_TAG, useUnmergedTree = true)

    private companion object {
        const val SECRET = "RECENTSPROBE-XYZ"
    }
}
