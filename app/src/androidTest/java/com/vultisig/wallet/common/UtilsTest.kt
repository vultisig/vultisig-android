package com.vultisig.wallet.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
class UtilsTest {
    @Test
    fun testDeviceName() {
        val deviceName = Utils.deviceName(getInstrumentation().getTargetContext())
        assertThat(deviceName, not(isEmptyString()))
    }
}