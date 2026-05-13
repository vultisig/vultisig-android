package com.vultisig.wallet.ui.models.keysign

import java.math.BigInteger
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class IsUnlimitedApprovalTest {

    private val json = Json

    // MAX_UINT256 as hex and decimal
    private val maxUint256Hex = "0x" + "f".repeat(64)
    private val maxUint256Dec = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE).toString()

    // 2^96 - 1 is below threshold; 2^96 is at/above threshold
    private val belowThresholdDec = BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE).toString()
    private val atThresholdDec = BigInteger.ONE.shiftLeft(96).toString()

    @Test
    fun `approve with MAX_UINT256 decimal is unlimited`() {
        assertTrue(
            isUnlimitedApproval(
                "approve(address,uint256)",
                """["0xSpender", "$maxUint256Dec"]""",
                json,
            )
        )
    }

    @Test
    fun `approve with MAX_UINT256 hex is unlimited`() {
        assertTrue(
            isUnlimitedApproval(
                "approve(address,uint256)",
                """["0xSpender", "$maxUint256Hex"]""",
                json,
            )
        )
    }

    @Test
    fun `approve with amount at threshold is unlimited`() {
        assertTrue(
            isUnlimitedApproval(
                "approve(address,uint256)",
                """["0xSpender", "$atThresholdDec"]""",
                json,
            )
        )
    }

    @Test
    fun `approve with amount below threshold is not unlimited`() {
        assertFalse(
            isUnlimitedApproval(
                "approve(address,uint256)",
                """["0xSpender", "$belowThresholdDec"]""",
                json,
            )
        )
    }

    @Test
    fun `approve with spaces in signature is detected`() {
        assertTrue(
            isUnlimitedApproval(
                "approve( address , uint256 )",
                """["0xSpender", "$maxUint256Dec"]""",
                json,
            )
        )
    }

    @Test
    fun `approve with uint (no width) is detected`() {
        assertTrue(
            isUnlimitedApproval(
                "approve(address,uint)",
                """["0xSpender", "$maxUint256Dec"]""",
                json,
            )
        )
    }

    @Test
    fun `permit with MAX_UINT256 at index 2 is unlimited`() {
        // permit(owner,spender,value,deadline,v,r,s) — first uint is at index 2
        assertTrue(
            isUnlimitedApproval(
                "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)",
                """["0xOwner", "0xSpender", "$maxUint256Dec", "9999999", "27", "0xR", "0xS"]""",
                json,
            )
        )
    }

    @Test
    fun `transfer is not an approval function`() {
        assertFalse(
            isUnlimitedApproval(
                "transfer(address,uint256)",
                """["0xRecipient", "$maxUint256Dec"]""",
                json,
            )
        )
    }

    @Test
    fun `null inputs returns false`() {
        assertFalse(isUnlimitedApproval("approve(address,uint256)", null, json))
    }

    @Test
    fun `malformed JSON inputs returns false`() {
        assertFalse(isUnlimitedApproval("approve(address,uint256)", "not-json", json))
    }

    @Test
    fun `missing amount arg returns false`() {
        assertFalse(isUnlimitedApproval("approve(address,uint256)", """["0xSpender"]""", json))
    }

    @Test
    fun `firstUintParamIndex approve`() {
        assertEquals(1, firstUintParamIndex("approve(address,uint256)"))
    }

    @Test
    fun `firstUintParamIndex permit`() {
        assertEquals(
            2,
            firstUintParamIndex("permit(address,address,uint256,uint256,uint8,bytes32,bytes32)"),
        )
    }

    @Test
    fun `firstUintParamIndex no uint param`() {
        assertNull(firstUintParamIndex("transfer(address,bytes32)"))
    }

    @Test
    fun `firstUintParamIndex empty params`() {
        assertNull(firstUintParamIndex("fallback()"))
    }
}
