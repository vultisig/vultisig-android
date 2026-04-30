package com.vultisig.wallet.data.repositories

/**
 * Static lookup of common EVM 4-byte function selectors → canonical text signatures.
 *
 * Consulted before hitting the 4byte directory so widely-used calls (ERC-20, WETH, Uniswap V2/V3,
 * Universal Router, Aave V2/V3, ERC-721/1155 transfers, multicall) decode instantly and offline.
 * Keys are the lowercase 8-character hex selector with no `0x` prefix, matching the format produced
 * by `String.stripHexPrefix().substring(0, 8)`.
 */
internal object EvmCommonSelectors {

    private val table: Map<String, String> =
        mapOf(
            // ERC-20
            "a9059cbb" to "transfer(address,uint256)",
            "23b872dd" to "transferFrom(address,address,uint256)",
            "095ea7b3" to "approve(address,uint256)",
            "39509351" to "increaseAllowance(address,uint256)",
            "a457c2d7" to "decreaseAllowance(address,uint256)",
            // EIP-2612 permit (USDC, most ERC-20 tokens)
            "d505accf" to "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)",
            // DAI-style permit (legacy MakerDAO shape, distinct from EIP-2612)
            "8fcbaf0c" to "permit(address,address,uint256,uint256,bool,uint8,bytes32,bytes32)",
            // WETH (and most wrapped-native tokens)
            "d0e30db0" to "deposit()",
            "2e1a7d4d" to "withdraw(uint256)",
            // Uniswap V2 router
            "38ed1739" to "swapExactTokensForTokens(uint256,uint256,address[],address,uint256)",
            "8803dbee" to "swapTokensForExactTokens(uint256,uint256,address[],address,uint256)",
            "7ff36ab5" to "swapExactETHForTokens(uint256,address[],address,uint256)",
            "4a25d94a" to "swapTokensForExactETH(uint256,uint256,address[],address,uint256)",
            "18cbafe5" to "swapExactTokensForETH(uint256,uint256,address[],address,uint256)",
            "fb3bdb41" to "swapETHForExactTokens(uint256,address[],address,uint256)",
            "5c11d795" to
                "swapExactTokensForTokensSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)",
            "b6f9de95" to
                "swapExactETHForTokensSupportingFeeOnTransferTokens(uint256,address[],address,uint256)",
            "791ac947" to
                "swapExactTokensForETHSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)",
            // Uniswap V3 SwapRouter (V01, with deadline)
            "414bf389" to
                "exactInputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))",
            "c04b8d59" to "exactInput((bytes,address,uint256,uint256,uint256))",
            "db3e2198" to
                "exactOutputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))",
            "f28c0498" to "exactOutput((bytes,address,uint256,uint256,uint256))",
            // Uniswap V3 SwapRouter02 (deadline dropped — used on most chains today)
            "04e45aaf" to
                "exactInputSingle((address,address,uint24,address,uint256,uint256,uint160))",
            "b858183f" to "exactInput((bytes,address,uint256,uint256))",
            "5023b4df" to
                "exactOutputSingle((address,address,uint24,address,uint256,uint256,uint160))",
            "09b81346" to "exactOutput((bytes,address,uint256,uint256))",
            // Uniswap Universal Router (current Uniswap UI swap entry point)
            "3593564c" to "execute(bytes,bytes[],uint256)",
            // Generic compose pattern — used by Uniswap V3, Aave V3, ENS, and many other contracts
            "ac9650d8" to "multicall(bytes[])",
            // Aave V3 / Spark / Radiant pool. `withdraw`, `borrow`, `repay` share signatures with
            // Aave V2; only `supply` is the V3 rename of V2
            // `deposit(address,uint256,address,uint16)`.
            "617ba037" to "supply(address,uint256,address,uint16)",
            "69328dec" to "withdraw(address,uint256,address)",
            "a415bcad" to "borrow(address,uint256,uint256,uint16,address)",
            "573ade81" to "repay(address,uint256,uint256,address)",
            // ERC-721 / ERC-1155 (`setApprovalForAll` is part of both standards)
            "42842e0e" to "safeTransferFrom(address,address,uint256)",
            "b88d4fde" to "safeTransferFrom(address,address,uint256,bytes)",
            "a22cb465" to "setApprovalForAll(address,bool)",
            "f242432a" to "safeTransferFrom(address,address,uint256,uint256,bytes)",
            "2eb2c2d6" to "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)",
            // Common staking shapes (not standardized — many farms use deposit/enter/mint instead)
            "a694fc3a" to "stake(uint256)",
            "2e17de78" to "unstake(uint256)",
        )

    fun lookup(selector: String): String? = table[selector.lowercase()]

    internal val entries: Map<String, String>
        get() = table
}
