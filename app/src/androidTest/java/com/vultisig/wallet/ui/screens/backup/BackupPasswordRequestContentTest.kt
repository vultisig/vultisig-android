package com.vultisig.wallet.ui.screens.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class BackupPasswordRequestContentTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val compose = createComposeRule()

    private var encryptedExports = 0
    private var unencryptedExports = 0

    @Test
    fun encryptedExportIsFirstAndOpensPasswordSetup() {
        startSettingsBackup()

        val encrypted = compose.onNodeWithTag(BackupPasswordRequestScreenTags.BACKUP_WITH_PASSWORD)
        val unencrypted =
            compose.onNodeWithTag(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD)
        unencrypted.performScrollTo()

        assertTrue(
            encrypted.getUnclippedBoundsInRoot().top < unencrypted.getUnclippedBoundsInRoot().top
        )
        encrypted.performClick()

        compose.runOnIdle {
            assertEquals(1, encryptedExports)
            assertEquals(0, unencryptedExports)
        }
    }

    @Test
    fun unencryptedExportRequiresConfirmationAndDismissalDoesNotExport() {
        startSettingsBackup()

        val unencrypted =
            compose.onNodeWithTag(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD)
        val warning =
            compose.onNodeWithText(text(R.string.backup_password_request_unencrypted_warning))
        unencrypted.performScrollTo().performClick()
        warning.assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, unencryptedExports) }

        compose
            .onNodeWithText(text(R.string.backup_password_request_unencrypted_cancel))
            .performClick()
        warning.assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, unencryptedExports) }

        unencrypted.performClick()
        compose
            .onNodeWithText(text(R.string.backup_password_request_no_password_action))
            .performClick()

        compose.runOnIdle {
            assertEquals(0, encryptedExports)
            assertEquals(1, unencryptedExports)
        }
    }

    private fun startSettingsBackup() {
        compose.setContent {
            OnBoardingComposeTheme {
                BackupPasswordRequestContent(
                    onNoPasswordClick = { unencryptedExports++ },
                    onWithPasswordClick = { encryptedExports++ },
                    onBackClick = {},
                    isSettingsLook = true,
                )
            }
        }
    }

    private fun text(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
