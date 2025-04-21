package com.vultisig.wallet.util

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.vultisig.wallet.app.activity.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule

open class CleanTest {

    @get:Rule(order = 0)
    val hintRule = HiltAndroidRule(this)

    @get:Rule(order = LAST_ORDER_INDEX)
    val compose = createEmptyComposeRule()

    open fun setUp() {
        clearAppData()

        hintRule.inject()
    }

    fun launchMainActivity(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java)

    companion object {
        const val LAST_ORDER_INDEX = 1
    }
}