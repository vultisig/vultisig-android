package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.BiometricUnlockAvailability
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsUiModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The row asks the device what it can do before turning the shortcut on, and must not ask before
 * turning it off — the two directions want different things from the hardware.
 */
@HiltAndroidTest
class PasscodeSettingsBiometricRowTest {

    // The screen needs nothing injected, but the test application's Hilt component does have to
    // exist: anything the system starts against this process mid-run — the messaging service, in
    // practice — asks for it on creation and takes the process down when it is not there.
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val compose = createComposeRule()

    private val biometricsRow =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.passcode_settings_biometrics)

    private val requested = mutableListOf<Boolean>()

    @Test
    fun theShortcutTurnsOnWhenTheDeviceCanHoldIt() {
        start(isEnabled = false, availability = BiometricUnlockAvailability.Available)

        compose.onNodeWithText(biometricsRow).performClick()

        assertEquals(listOf(true), requested)
    }

    @Test
    fun theShortcutCannotBeTurnedOnWithNothingEnrolled() {
        start(isEnabled = false, availability = BiometricUnlockAvailability.NotEnrolled)

        compose.onNodeWithText(biometricsRow).performClick()

        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun theShortcutStillTurnsOffAfterTheLastBiometricIsDeleted() {
        // Deleting the last enrolled biometric invalidates the keystore key rather than removing
        // it, so the store goes on reading as enabled while the device reports nothing enrolled.
        // Gating this direction on the hardware would leave the switch stuck at ON with the stored
        // copy unremovable from here.
        start(isEnabled = true, availability = BiometricUnlockAvailability.NotEnrolled)

        compose.onNodeWithText(biometricsRow).performClick()

        assertEquals(listOf(false), requested)
    }

    @Test
    fun theShortcutStillTurnsOffOnHardwareThatCannotAuthenticateAtAll() {
        start(isEnabled = true, availability = BiometricUnlockAvailability.Unavailable)

        compose.onNodeWithText(biometricsRow).performClick()

        assertEquals(listOf(false), requested)
    }

    private fun start(isEnabled: Boolean, availability: BiometricUnlockAvailability) {
        compose.setContent {
            PasscodeSettingsScreen(
                state =
                    PasscodeSettingsUiModel(
                        isPasscodeEnabled = true,
                        autoLockTimeout = AutoLockTimeout.Never,
                        isReady = true,
                        isBiometricUnlockEnabled = isEnabled,
                    ),
                biometricAvailability = availability,
                onBackClick = {},
                onPasscodeEnabledChange = {},
                onChangePasscodeClick = {},
                onAutoLockClick = {},
                onBiometricUnlockChange = { requested += it },
            )
        }
    }
}
