package com.vultisig.wallet.common

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
class UtilsTest {
    @Test
    fun testDeviceName() {
        val deviceName = Utils.deviceName
        assertThat(deviceName, not(isEmptyString()))
    }
}