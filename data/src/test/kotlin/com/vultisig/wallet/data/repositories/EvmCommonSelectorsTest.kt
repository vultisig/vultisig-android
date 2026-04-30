package com.vultisig.wallet.data.repositories

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class EvmCommonSelectorsTest {

    @Test
    fun `ERC-20 transfer selector resolves`() {
        assertEquals("transfer(address,uint256)", EvmCommonSelectors.lookup("a9059cbb"))
    }

    @Test
    fun `ERC-20 approve selector resolves`() {
        assertEquals("approve(address,uint256)", EvmCommonSelectors.lookup("095ea7b3"))
    }

    @Test
    fun `WETH deposit and withdraw selectors resolve`() {
        assertEquals("deposit()", EvmCommonSelectors.lookup("d0e30db0"))
        assertEquals("withdraw(uint256)", EvmCommonSelectors.lookup("2e1a7d4d"))
    }

    @Test
    fun `Uniswap V2 swap selectors resolve`() {
        assertEquals(
            "swapExactTokensForTokens(uint256,uint256,address[],address,uint256)",
            EvmCommonSelectors.lookup("38ed1739"),
        )
        assertEquals(
            "swapExactETHForTokens(uint256,address[],address,uint256)",
            EvmCommonSelectors.lookup("7ff36ab5"),
        )
    }

    @Test
    fun `Aave V3 supply borrow repay selectors resolve`() {
        assertEquals(
            "supply(address,uint256,address,uint16)",
            EvmCommonSelectors.lookup("617ba037"),
        )
        assertEquals(
            "borrow(address,uint256,uint256,uint16,address)",
            EvmCommonSelectors.lookup("a415bcad"),
        )
        assertEquals(
            "repay(address,uint256,uint256,address)",
            EvmCommonSelectors.lookup("573ade81"),
        )
    }

    @Test
    fun `ERC-721 and ERC-1155 transfer selectors resolve`() {
        assertEquals(
            "safeTransferFrom(address,address,uint256)",
            EvmCommonSelectors.lookup("42842e0e"),
        )
        assertEquals(
            "safeTransferFrom(address,address,uint256,bytes)",
            EvmCommonSelectors.lookup("b88d4fde"),
        )
        assertEquals(
            "safeTransferFrom(address,address,uint256,uint256,bytes)",
            EvmCommonSelectors.lookup("f242432a"),
        )
        assertEquals(
            "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)",
            EvmCommonSelectors.lookup("2eb2c2d6"),
        )
    }

    @Test
    fun `Uniswap V2 fee-on-transfer variants resolve`() {
        assertEquals(
            "swapExactTokensForTokensSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)",
            EvmCommonSelectors.lookup("5c11d795"),
        )
        assertEquals(
            "swapExactETHForTokensSupportingFeeOnTransferTokens(uint256,address[],address,uint256)",
            EvmCommonSelectors.lookup("b6f9de95"),
        )
        assertEquals(
            "swapExactTokensForETHSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)",
            EvmCommonSelectors.lookup("791ac947"),
        )
    }

    @Test
    fun `Uniswap V3 SwapRouter and multicall selectors resolve`() {
        assertEquals(
            "exactInputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))",
            EvmCommonSelectors.lookup("414bf389"),
        )
        assertEquals(
            "exactInput((bytes,address,uint256,uint256,uint256))",
            EvmCommonSelectors.lookup("c04b8d59"),
        )
        assertEquals("multicall(bytes[])", EvmCommonSelectors.lookup("ac9650d8"))
    }

    @Test
    fun `Uniswap V3 SwapRouter02 selectors resolve`() {
        assertEquals(
            "exactInputSingle((address,address,uint24,address,uint256,uint256,uint160))",
            EvmCommonSelectors.lookup("04e45aaf"),
        )
        assertEquals(
            "exactInput((bytes,address,uint256,uint256))",
            EvmCommonSelectors.lookup("b858183f"),
        )
        assertEquals(
            "exactOutputSingle((address,address,uint24,address,uint256,uint256,uint160))",
            EvmCommonSelectors.lookup("5023b4df"),
        )
        assertEquals(
            "exactOutput((bytes,address,uint256,uint256))",
            EvmCommonSelectors.lookup("09b81346"),
        )
    }

    @Test
    fun `Universal Router execute selector resolves`() {
        assertEquals("execute(bytes,bytes[],uint256)", EvmCommonSelectors.lookup("3593564c"))
    }

    @Test
    fun `EIP-2612 and DAI permit selectors resolve`() {
        assertEquals(
            "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)",
            EvmCommonSelectors.lookup("d505accf"),
        )
        assertEquals(
            "permit(address,address,uint256,uint256,bool,uint8,bytes32,bytes32)",
            EvmCommonSelectors.lookup("8fcbaf0c"),
        )
    }

    @Test
    fun `lookup is case-insensitive`() {
        assertEquals("approve(address,uint256)", EvmCommonSelectors.lookup("095EA7B3"))
    }

    @Test
    fun `unknown selector returns null`() {
        assertNull(EvmCommonSelectors.lookup("deadbeef"))
    }

    @Test
    fun `every entry has 8 lowercase hex selector and parseable name(types) signature`() {
        val hexKey = Regex("^[0-9a-f]{8}$")
        val signature = Regex("^[a-zA-Z_][a-zA-Z0-9_]*\\(.*\\)$")

        val entries = EvmCommonSelectors.entries
        assertTrue(entries.isNotEmpty(), "table must not be empty")

        for ((selector, sig) in entries) {
            assertTrue(
                hexKey.matches(selector),
                "selector '$selector' must be 8 lowercase hex chars",
            )
            assertTrue(signature.matches(sig), "signature '$sig' must look like name(types)")
            assertEquals(
                sig.count { it == '(' },
                sig.count { it == ')' },
                "unbalanced parentheses in '$sig'",
            )
            assertEquals(
                sig,
                EvmCommonSelectors.lookup(selector),
                "lookup('$selector') must round-trip to the same signature",
            )
        }
    }
}
