package com.vultisig.wallet.app.activity.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The passcode lock draws in a window of its own, which only covers the windows that were already
 * open when it was added. A `dialog<>` destination gets a window too, so one left open when the app
 * locks has to go — and, because the routing collectors keep running behind a closed gate, so does
 * one routed afterwards.
 */
class PopDialogDestinationsWhileLockedTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private var isLocked by mutableStateOf(false)

    @Test
    fun aDialogDestinationRoutedWhileAlreadyLockedIsPoppedAgain() {
        start(locked = true)

        navigateTo(Sheet)

        assertSettlesOn(Home)
    }

    @Test
    fun theDialogDestinationsOpenWhenTheLockArrivesAreAllPopped() {
        start(locked = false)
        navigateTo(Sheet)
        navigateTo(OtherSheet)
        assertSettlesOn(OtherSheet)

        setLockState(true)

        assertSettlesOn(Home)
    }

    @Test
    fun aDialogDestinationIsLeftAloneWhileTheAppIsUnlocked() {
        start(locked = false)

        navigateTo(Sheet)

        assertSettlesOn(Sheet)
    }

    /** Unlocking hands the back stack back, or every dialog would be one-shot from then on. */
    @Test
    fun aDialogDestinationRoutedAfterUnlockingSurvives() {
        start(locked = true)

        setLockState(false)
        navigateTo(Sheet)

        assertSettlesOn(Sheet)
    }

    private fun start(locked: Boolean) {
        isLocked = locked
        compose.setContent {
            navController = rememberNavController()
            PopDialogDestinationsWhileLocked(navController, isLocked)
            NavHost(navController = navController, startDestination = Home) {
                composable(Home) { Filler() }
                dialog(Sheet) { Filler() }
                dialog(OtherSheet) { Filler() }
            }
        }
        assertSettlesOn(Home)
    }

    /**
     * Waits for the change to reach composition, so the effect has started or stopped before the
     * next step.
     */
    private fun setLockState(locked: Boolean) {
        isLocked = locked
        compose.waitForIdle()
    }

    private fun navigateTo(route: String) {
        compose.runOnUiThread { navController.navigate(route) }
    }

    private fun assertSettlesOn(route: String) {
        compose.waitForIdle()
        compose.waitUntil(SettleTimeoutMillis) { currentRoute() == route }

        assertEquals(route, currentRoute())
    }

    private fun currentRoute(): String? = navController.currentBackStackEntry?.destination?.route

    @Composable
    private fun Filler() {
        Box(Modifier.fillMaxSize())
    }

    private companion object {
        const val Home = "home"
        const val Sheet = "sheet"
        const val OtherSheet = "otherSheet"

        const val SettleTimeoutMillis = 5_000L
    }
}
