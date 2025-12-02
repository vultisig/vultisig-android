package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.common.toHexByteArray


internal fun decodeGeneric(memo: String, signature: String): String {
    try {
        // Extract function selector (first 4 bytes after 0x)
        val functionSelector = if (memo.startsWith("0x")) {
            memo.substring(2, 10)
        } else {
            memo.take(8)
        }

        // Parse signature to extract function name and parameter types
        val (functionName, paramTypes) = parseSignature(signature)

        // Build ABI JSON dynamically
        val abi = buildAbiJson(functionSelector, functionName, paramTypes)

        // Decode using wallet core
        val call = memo.toHexByteArray()
        val decoded = wallet.core.jni.EthereumAbi.decodeCall(call, abi)

        return decoded
    } catch (e: Exception) {
        return "Error decoding: ${e.message}"
    }
}

/**
 * Parse function signature into name and parameter types
 * Example: "send((uint32,bytes32),address)" -> ("send", ["(uint32,bytes32)", "address"])
 */
private fun parseSignature(signature: String): Pair<String, List<String>> {
    val openParenIndex = signature.indexOf('(')
    if (openParenIndex == -1) {
        throw IllegalArgumentException("Invalid signature format")
    }

    val functionName = signature.take(openParenIndex)
    val paramsString = signature.substring(openParenIndex + 1, signature.lastIndexOf(')'))

    // Parse parameters considering nested tuples
    val paramTypes = parseParameters(paramsString)

    return Pair(functionName, paramTypes)
}

/**
 * Parse parameter string handling nested tuples
 * Example: "(uint32,bytes32),address,uint256" -> ["(uint32,bytes32)", "address", "uint256"]
 */
private fun parseParameters(paramsString: String): List<String> {
    if (paramsString.isEmpty()) return emptyList()

    val params = mutableListOf<String>()
    var currentParam = StringBuilder()
    var depth = 0

    for (char in paramsString) {
        when (char) {
            '(' -> {
                depth++
                currentParam.append(char)
            }
            ')' -> {
                depth--
                currentParam.append(char)
            }
            ',' -> {
                if (depth == 0) {
                    params.add(currentParam.toString().trim())
                    currentParam = StringBuilder()
                } else {
                    currentParam.append(char)
                }
            }
            else -> currentParam.append(char)
        }
    }

    if (currentParam.isNotEmpty()) {
        params.add(currentParam.toString().trim())
    }

    return params
}

/**
 * Build ABI JSON from function components
 */
private fun buildAbiJson(selector: String, functionName: String, paramTypes: List<String>): String {
    val inputs = paramTypes.mapIndexed { index, type ->
        buildInputJson("param$index", type)
    }.joinToString(",")

    return """
        {
            "$selector": {
                "constant": false,
                "inputs": [$inputs],
                "name": "$functionName",
                "outputs": [],
                "payable": false,
                "stateMutability": "nonpayable",
                "type": "function"
            }
        }
        """.trimIndent()
}

/**
 * Build JSON for a single input parameter, handling tuples recursively
 */
private fun buildInputJson(name: String, type: String): String {
    return if (type.startsWith("(")) {
        // Handle tuple type
        val tupleContent = type.substring(1, type.lastIndexOf(')'))
        val isArray = type.endsWith("[]")
        val baseType = if (isArray) "tuple[]" else "tuple"

        val components = parseParameters(tupleContent)
        val componentsJson = components.mapIndexed { index, componentType ->
            buildInputJson("field$index", componentType)
        }.joinToString(",")

        """
            {
                "name": "$name",
                "type": "$baseType",
                "components": [$componentsJson]
            }
            """.trimIndent()
    } else {
        // Simple type
        """
            {
                "name": "$name",
                "type": "$type"
            }
            """.trimIndent()
    }
}