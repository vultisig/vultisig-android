package com.vultisig.wallet.ui.utils

import androidx.test.core.app.ActivityScenario
import com.vultisig.wallet.app.activity.MainActivity

fun ActivityScenario<MainActivity>.back() {
    onActivity {
        it.onBackPressedDispatcher.onBackPressed()
    }
}