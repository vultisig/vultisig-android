package com.vultisig.wallet.app.activity.components

import androidx.navigation.Navigator
import androidx.navigation.compose.DialogNavigator
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the one string [DIALOG_NAVIGATOR_NAME] cannot get from the library itself.
 *
 * `DialogNavigator.NAME` is internal to navigation-compose, so the auto-lock guard identifies
 * dialog destinations by duplicating the identifier. If a library upgrade ever renames it, that
 * comparison silently stops matching and a `dialog<>` sheet is left floating above the lock screen,
 * still taking input — the failure this whole matcher exists to prevent, back with no symptom at
 * compile time. The library declares the name by annotation, so a test can read the real one.
 */
internal class DialogNavigatorNameTest {

    @Test
    fun `the guard matches the navigator name the library actually registers`() {
        val declared = DialogNavigator::class.java.getAnnotation(Navigator.Name::class.java)?.value

        assertEquals(declared, DIALOG_NAVIGATOR_NAME)
    }
}
