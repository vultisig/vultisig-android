package com.vultisig.wallet.common

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.vultisig.wallet.data.common.Utils
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.isEmptyString
import org.hamcrest.Matchers.not
import org.junit.Test

class UtilsTest {
    @Test
    fun testDeviceName() {
        val deviceName = Utils.deviceName(getInstrumentation().getTargetContext())
        assertThat(deviceName, not(isEmptyString()))
    }
}