package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.BiometricUnlockAvailability
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsUiModel
import org.junit.Rule
import org.junit.Test

/**
 * The rows under the switch are conditional on a passcode existing, so the caption sits one indent
 * away from inheriting that condition, and it is worth most in the state the condition excludes.
 */
class PasscodeSettingsExplanationTest {

    @get:Rule val compose = createComposeRule()

    private val explanation =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.passcode_settings_encryption_explanation)

    @Test
    fun theExplanationShowsBeforeAPasscodeIsSet() {
        start(passcodeEnabled = false)

        compose.onNodeWithText(explanation).assertIsDisplayed()
    }

    @Test
    fun theExplanationShowsOnceAPasscodeIsSet() {
        start(passcodeEnabled = true)

        compose.onNodeWithText(explanation).assertIsDisplayed()
    }

    private fun start(passcodeEnabled: Boolean) {
        compose.setContent {
            PasscodeSettingsScreen(
                state =
                    PasscodeSettingsUiModel(
                        isPasscodeEnabled = passcodeEnabled,
                        autoLockTimeout = AutoLockTimeout.Never,
                        isReady = true,
                    ),
                biometricAvailability = BiometricUnlockAvailability.Available,
                onBackClick = {},
                onPasscodeEnabledChange = {},
                onChangePasscodeClick = {},
                onAutoLockClick = {},
                onBiometricUnlockChange = {},
            )
        }
    }
}
