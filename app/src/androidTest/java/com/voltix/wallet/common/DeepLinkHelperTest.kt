package com.voltix.wallet.common

import com.voltix.wallet.models.TssAction
import org.junit.jupiter.api.Assertions.*

import org.junit.Test

class DeepLinkHelperTest {
    private val testInput = "voltix:?type=NewVault&tssType=Reshare&jsonData=xxx"
    @Test
    fun getJsonData() {
        val deepLinkHelper = DeepLinkHelper(testInput)
        assertEquals("xxx", deepLinkHelper.getJsonData(testInput))
    }

    @Test
    fun getFlowType() {
        val deepLinkHelper = DeepLinkHelper(testInput)
        assertEquals("NewVault", deepLinkHelper.getFlowType())
    }

    @Test
    fun getTssAction() {
        val deepLinkHelper = DeepLinkHelper(testInput)
        assertEquals(TssAction.ReShare, deepLinkHelper.getTssAction())
    }
}