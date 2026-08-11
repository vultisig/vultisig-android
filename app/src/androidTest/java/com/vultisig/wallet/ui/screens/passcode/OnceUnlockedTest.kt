package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.home.MonthlyBackupReminder
import org.junit.Rule
import org.junit.Test

/**
 * Driven through `MonthlyBackupReminder`, the sheet the home screen raises off a background read
 * with no user action — so its window can be added after the passcode lock's and draw above it.
 * Composed here the way the home screen composes it: the caller's own condition, then the gate.
 */
class OnceUnlockedTest {

    @get:Rule val compose = createComposeRule()

    private var isGateClosed by mutableStateOf(false)
    private var isReminderWanted by mutableStateOf(false)

    @Test
    fun theReminderShowsWhileTheGateIsOpen() {
        start(gateClosed = false)

        wantReminder(true)

        assertReminderShows()
    }

    @Test
    fun theReminderIsHeldWhileTheGateIsClosed() {
        start(gateClosed = true)

        wantReminder(true)

        assertReminderHeld()
    }

    @Test
    fun theHeldReminderShowsOnceTheGateOpens() {
        start(gateClosed = true)
        wantReminder(true)
        assertReminderHeld()

        setGateState(closed = false)

        assertReminderShows()
    }

    /**
     * The lock's own window covers a sheet that was already up, and dropping it would discard the
     * screen state behind it.
     */
    @Test
    fun theReminderAlreadyUpWhenTheGateClosesStaysUp() {
        start(gateClosed = false)
        wantReminder(true)
        assertReminderShows()

        setGateState(closed = true)

        assertReminderShows()
    }

    /** Held, not queued: what is no longer wanted by the time the gate opens is not raised. */
    @Test
    fun theHeldReminderIsDroppedWhenItStopsBeingWanted() {
        start(gateClosed = true)
        wantReminder(true)

        wantReminder(false)
        setGateState(closed = false)

        assertReminderHeld()
    }

    /**
     * The hold is per showing, so dismissing and being raised again behind the lock still holds.
     */
    @Test
    fun theReminderRaisedAgainAfterTheGateClosesIsHeld() {
        start(gateClosed = false)
        wantReminder(true)
        assertReminderShows()
        wantReminder(false)

        setGateState(closed = true)
        wantReminder(true)

        assertReminderHeld()
    }

    private fun start(gateClosed: Boolean) {
        isGateClosed = gateClosed
        compose.setContent {
            CompositionLocalProvider(LocalIsGateClosed provides isGateClosed) {
                if (isReminderWanted) {
                    OnceUnlocked {
                        MonthlyBackupReminder(onDismiss = {}, onBackup = {}, onDoNotRemind = {})
                    }
                }
            }
        }
    }

    private fun wantReminder(wanted: Boolean) {
        isReminderWanted = wanted
        compose.waitForIdle()
    }

    private fun setGateState(closed: Boolean) {
        isGateClosed = closed
        compose.waitForIdle()
    }

    private fun assertReminderShows() {
        compose.waitUntil(SettleTimeoutMillis) { isReminderComposed() }

        compose.onNodeWithText(reminderTitle).assertExists()
    }

    private fun assertReminderHeld() {
        compose.waitForIdle()

        compose.onNodeWithText(reminderTitle).assertDoesNotExist()
    }

    private fun isReminderComposed(): Boolean =
        compose.onAllNodesWithText(reminderTitle).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        val reminderTitle: String =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getString(R.string.monthly_backup_reminder_title)

        const val SettleTimeoutMillis = 5_000L
    }
}
