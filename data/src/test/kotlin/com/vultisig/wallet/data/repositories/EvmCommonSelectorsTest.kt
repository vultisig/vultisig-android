package com.vultisig.wallet.data.repositories

import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `lookup is case-insensitive`() {
        assertEquals("approve(address,uint256)", EvmCommonSelectors.lookup("095EA7B3"))
    }

    @Test
    fun `unknown selector returns null`() {
        assertNull(EvmCommonSelectors.lookup("deadbeef"))
    }
}
