package com.vultisig.wallet.data.repositories

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

data class TokenAndAmount(val tokenAddress: String, val rawAmount: String)

sealed interface ExtractionStrategy {
    /**
     * Lending/staking pattern: supply(address asset, uint256 amount, ...). Requires address index <
     * uint256 index to avoid ERC-4626 collisions.
     */
    data object FirstAddressBeforeFirstUint : ExtractionStrategy

    /**
     * ERC20 methods called on the token contract itself. Token = toAddress of the tx, amount =
     * first uint256 param.
     */
    data object ContractIsToken : ExtractionStrategy

    /**
     * Token is the Nth address param (0-indexed). e.g. Compound V3 supplyTo(address dst, address
     * asset, uint256 amount) -> 1
     */
    data class NthAddress(val index: Int) : ExtractionStrategy
}

/**
 * Split a param list on top-level commas only, respecting nested parentheses so tuple types like
 * `(uint256,uint256)` stay intact as one param.
 */
private fun splitTopLevel(params: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    val current = StringBuilder()
    for (ch in params) {
        when (ch) {
            '(' -> depth++
            ')' -> depth--
        }
        if (ch == ',' && depth == 0) {
            parts.add(current.toString().trim())
            current.clear()
        } else {
            current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        parts.add(current.toString().trim())
    }
    return parts
}

object ContractCallExtractor {

    private val registry: Map<String, ExtractionStrategy> =
        mapOf(
            // Aave V3 / Spark / Radiant
            "supply" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "supplyWithPermit" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "withdraw" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "borrow" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "repay" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "repayWithPermit" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            "repayWithATokens" to ExtractionStrategy.FirstAddressBeforeFirstUint,
            // Compound V3
            "supplyTo" to ExtractionStrategy.NthAddress(1),
            "withdrawTo" to ExtractionStrategy.NthAddress(1),
            "transferAsset" to ExtractionStrategy.NthAddress(1),
            // EigenLayer
            "depositIntoStrategy" to ExtractionStrategy.NthAddress(1),
            "depositIntoStrategyWithSignature" to ExtractionStrategy.NthAddress(1),
            // Across Protocol V3
            "depositV3" to ExtractionStrategy.NthAddress(2),
            // ERC20 methods on the token contract itself
            "transfer" to ExtractionStrategy.ContractIsToken,
            "transferFrom" to ExtractionStrategy.ContractIsToken,
            "approve" to ExtractionStrategy.ContractIsToken,
            "increaseAllowance" to ExtractionStrategy.ContractIsToken,
            "decreaseAllowance" to ExtractionStrategy.ContractIsToken,
        )

    private val json = Json { ignoreUnknownKeys = true }

    fun extract(signature: String, argsJson: String, toAddress: String?): TokenAndAmount? {
        val parenStart = signature.indexOf('(')
        val parenEnd = signature.lastIndexOf(')')
        if (parenStart == -1 || parenEnd == -1) return null

        val funcName = signature.substring(0, parenStart).trim()
        val strategy = registry[funcName] ?: return null

        val paramTypes = splitTopLevel(signature.substring(parenStart + 1, parenEnd))

        val args =
            runCatching {
                    (json.parseToJsonElement(argsJson) as? JsonArray)?.map {
                        it.jsonPrimitive.content
                    }
                }
                .getOrNull() ?: return null

        val uint256Idx = paramTypes.indexOf("uint256")
        if (uint256Idx == -1 || uint256Idx >= args.size) return null
        val rawAmount = args[uint256Idx]
        if (rawAmount.isEmpty() || !rawAmount.all { it.isDigit() }) return null

        return when (strategy) {
            ExtractionStrategy.ContractIsToken -> {
                if (toAddress.isNullOrEmpty()) null else TokenAndAmount(toAddress, rawAmount)
            }

            ExtractionStrategy.FirstAddressBeforeFirstUint -> {
                val addressIdx = paramTypes.indexOf("address")
                if (addressIdx == -1 || addressIdx >= uint256Idx || addressIdx >= args.size) {
                    null
                } else {
                    val tokenAddress = args[addressIdx]
                    if (tokenAddress.isEmpty()) null else TokenAndAmount(tokenAddress, rawAmount)
                }
            }

            is ExtractionStrategy.NthAddress -> {
                var count = 0
                var targetIdx = -1
                for ((i, type) in paramTypes.withIndex()) {
                    if (type == "address") {
                        if (count == strategy.index) {
                            targetIdx = i
                            break
                        }
                        count++
                    }
                }
                if (targetIdx == -1 || targetIdx >= args.size) {
                    null
                } else {
                    val tokenAddress = args[targetIdx]
                    if (tokenAddress.isEmpty()) null else TokenAndAmount(tokenAddress, rawAmount)
                }
            }
        }
    }
}
