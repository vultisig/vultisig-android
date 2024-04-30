package com.voltix.wallet.common

import org.junit.Assert.*
import org.junit.Test

class UtilsTest {
    @Test
    fun testDeviceName() {
        val deviceName = Utils.deviceName
        assertTrue(deviceName.isNotEmpty())
    }
}