package com.vultisig.wallet.common

import com.vultisig.wallet.models.TssAction
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class DeepLinkHelperTest {
    private val testInput = "vultisig:?type=NewVault&tssType=Reshare&jsonData=xxx"
    @Test
    fun getJsonData() {
        val deepLinkHelper = DeepLinkHelper(testInput)
        assertEquals("xxx", deepLinkHelper.getJsonData())
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