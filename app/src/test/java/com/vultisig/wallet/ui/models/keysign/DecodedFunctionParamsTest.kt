package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.UiText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class DecodedFunctionParamsTest {

    private val json = Json

    @Test
    fun `approve produces labelled spender and amount rows`() {
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = """["0x7a250d5630b4cf539739df2c5dacb4c659f2488d", "1000000"]""",
                json = json,
                tokenSymbol = "USDC",
            )

        assertNotNull(rows)
        assertEquals(2, rows.size)
        assertResId(R.string.erc20_approval_spender, rows[0].label)
        assertEquals(
            "0x7a250d5630b4cf539739df2c5dacb4c659f2488d",
            (rows[0].value as UiText.DynamicString).text,
        )
        assertResId(R.string.decoded_function_amount, rows[1].label)
        assertEquals("1000000 USDC", (rows[1].value as UiText.DynamicString).text)
    }

    @Test
    fun `approve with unlimited flag renders the unlimited amount in warning style`() {
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson =
                    """["0xabc", "115792089237316195423570985008687907853269984665640564039457584007913129639935"]""",
                json = json,
                tokenSymbol = "USDC",
                isUnlimitedApproval = true,
            )

        assertNotNull(rows)
        val amountRow = rows[1]
        assertTrue(amountRow.isWarning)
        val formatted = amountRow.value as UiText.FormattedText
        assertEquals(R.string.decoded_function_unlimited_amount, formatted.resId)
        assertEquals(listOf<Any>("USDC"), formatted.formatArgs)
    }

    @Test
    fun `approve with unlimited flag and unknown symbol falls back to bare Unlimited`() {
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = """["0xabc", "1"]""",
                json = json,
                tokenSymbol = null,
                isUnlimitedApproval = true,
            )

        val amountRow = assertNotNull(rows)[1]
        assertEquals(
            R.string.decoded_function_unlimited,
            (amountRow.value as UiText.StringResource).resId,
        )
        assertTrue(amountRow.isWarning)
    }

    @Test
    fun `transfer produces recipient and amount rows`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0x1111111111111111111111111111111111111111", "250"]""",
                json = json,
                tokenSymbol = "USDC",
            )

        assertNotNull(rows)
        assertEquals(2, rows.size)
        assertResId(R.string.verify_transaction_to_title, rows[0].label)
        assertResId(R.string.decoded_function_amount, rows[1].label)
        assertEquals("250 USDC", (rows[1].value as UiText.DynamicString).text)
    }

    @Test
    fun `transferFrom produces from recipient and amount rows`() {
        val rows =
            decodedFunctionParams(
                signature = "transferFrom(address,address,uint256)",
                inputsJson = """["0xaaa", "0xbbb", "42"]""",
                json = json,
                tokenSymbol = "USDC",
            )

        assertNotNull(rows)
        assertEquals(3, rows.size)
        assertResId(R.string.verify_transaction_from_title, rows[0].label)
        assertResId(R.string.verify_transaction_to_title, rows[1].label)
        assertResId(R.string.decoded_function_amount, rows[2].label)
    }

    @Test
    fun `permit produces owner spender amount and deadline rows`() {
        val rows =
            decodedFunctionParams(
                signature = "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)",
                inputsJson = """["0xowner","0xspender","10","9999","27","0x00","0x00"]""",
                json = json,
                tokenSymbol = "DAI",
            )

        assertNotNull(rows)
        assertEquals(4, rows.size)
        assertResId(R.string.decoded_function_owner, rows[0].label)
        assertResId(R.string.erc20_approval_spender, rows[1].label)
        assertResId(R.string.decoded_function_amount, rows[2].label)
        assertResId(R.string.decoded_function_deadline, rows[3].label)
        assertEquals("9999", (rows[3].value as UiText.DynamicString).text)
    }

    @Test
    fun `setApprovalForAll true emits the approved status with warning style`() {
        val rows =
            decodedFunctionParams(
                signature = "setApprovalForAll(address,bool)",
                inputsJson = """["0xoperator", "true"]""",
                json = json,
            )

        assertNotNull(rows)
        assertEquals(2, rows.size)
        assertResId(R.string.decoded_function_operator, rows[0].label)
        assertResId(R.string.decoded_function_status, rows[1].label)
        assertEquals(
            R.string.decoded_function_status_approved,
            (rows[1].value as UiText.StringResource).resId,
        )
        assertTrue(rows[1].isWarning)
    }

    @Test
    fun `setApprovalForAll false emits the revoked status without warning style`() {
        val rows =
            decodedFunctionParams(
                signature = "setApprovalForAll(address,bool)",
                inputsJson = """["0xoperator", "false"]""",
                json = json,
            )

        assertNotNull(rows)
        assertEquals(
            R.string.decoded_function_status_revoked,
            (rows[1].value as UiText.StringResource).resId,
        )
        assertEquals(false, rows[1].isWarning)
    }

    @Test
    fun `unknown function falls back to positional labels with type tags`() {
        val rows =
            decodedFunctionParams(
                signature = "doStuff(address,uint256,bool)",
                inputsJson = """["0xabc", "12345", "true"]""",
                json = json,
            )

        assertNotNull(rows)
        assertEquals(3, rows.size)
        assertEquals("#1 (address)", (rows[0].label as UiText.DynamicString).text)
        assertEquals("#2 (uint256)", (rows[1].label as UiText.DynamicString).text)
        assertEquals("#3 (bool)", (rows[2].label as UiText.DynamicString).text)
    }

    @Test
    fun `unknown function calls the contract label lookup for address parameters`() {
        val rows =
            decodedFunctionParams(
                signature = "doStuff(address,uint256)",
                inputsJson = """["0xabc", "1"]""",
                json = json,
                contractLabel = { if (it == "0xabc") "Custom Router" else null },
            )

        assertNotNull(rows)
        assertEquals("Custom Router", rows[0].secondary)
        assertNull(rows[1].secondary)
    }

    @Test
    fun `known function call resolves contract label on address arguments`() {
        val routerAddress = "0xE592427A0AEce92De3Edee1F18E0157C05861564"
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = """["$routerAddress", "1"]""",
                json = json,
                contractLabel = { addr ->
                    if (addr.equals(routerAddress, ignoreCase = true)) "Uniswap V3 Router" else null
                },
            )

        val spender = assertNotNull(rows).first()
        assertEquals("Uniswap V3 Router", spender.secondary)
    }

    @Test
    fun `known function call returns null secondary when contract label lookup misses`() {
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = """["0xdeaddeaddeaddeaddeaddeaddeaddeaddeaddead", "1"]""",
                json = json,
                contractLabel = { null },
            )

        val spender = assertNotNull(rows).first()
        assertNull(spender.secondary)
    }

    @Test
    fun `transfer scales the amount by token decimals`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "1000000"]""",
                json = json,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("1 USDC", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `transfer scales fractional amount and strips trailing zeros`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "1500000"]""",
                json = json,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("1.5 USDC", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `transfer with null decimals keeps the raw integer in the display`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "1000000"]""",
                json = json,
                tokenSymbol = "USDC",
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("1000000 USDC", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `transfer with hex amount scales correctly`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "0xf4240"]""",
                json = json,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("1 USDC", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `transfer with malformed amount falls back to the raw string`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "not-a-number"]""",
                json = json,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("not-a-number", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `transfer with implausible decimals falls back to the raw integer`() {
        val rows =
            decodedFunctionParams(
                signature = "transfer(address,uint256)",
                inputsJson = """["0xabc", "1000000"]""",
                json = json,
                tokenSymbol = "USDC",
                tokenDecimals = 255,
            )

        val amount = assertNotNull(rows)[1]
        assertEquals("1000000 USDC", (amount.value as UiText.DynamicString).text)
    }

    @Test
    fun `unknown signature shape with arity mismatch still produces generic rows`() {
        val rows =
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = """["0xabc"]""",
                json = json,
            )

        assertNotNull(rows)
        // Inputs underflowed by one; the generic fallback covers what is there + the missing slot
        // so the row count is the larger of the two and the missing slot has empty value.
        assertEquals(2, rows.size)
    }

    @Test
    fun `blank inputs produce null`() {
        assertNull(
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = "",
                json = json,
            )
        )
        assertNull(decodedFunctionParams(signature = "", inputsJson = "[]", json = json))
        assertNull(
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = null,
                json = json,
            )
        )
    }

    @Test
    fun `malformed JSON produces null`() {
        assertNull(
            decodedFunctionParams(
                signature = "approve(address,uint256)",
                inputsJson = "not-json",
                json = json,
            )
        )
    }

    @Test
    fun `signature without closing paren produces null`() {
        assertNull(
            decodedFunctionParams(
                signature = "approve(address,uint256",
                inputsJson = "[]",
                json = json,
            )
        )
    }

    @Test
    fun `tuple parameters are kept as a single positional row in unknown-function fallback`() {
        val rows =
            decodedFunctionParams(
                signature =
                    "exactInputSingle((address,address,uint24,address,uint256,uint256,uint160))",
                inputsJson = """[[ "0xa", "0xb", "500", "0xc", "1000", "100", "0" ]]""",
                json = json,
            )

        assertNotNull(rows)
        assertEquals(1, rows.size)
        assertEquals(
            "#1 ((address,address,uint24,address,uint256,uint256,uint160))",
            (rows[0].label as UiText.DynamicString).text,
        )
    }

    private fun assertResId(expected: Int, label: UiText) {
        val actual = label as UiText.StringResource
        assertEquals(expected, actual.resId)
    }
}
