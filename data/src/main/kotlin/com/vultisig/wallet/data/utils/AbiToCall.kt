package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.common.toHexByteArray


internal fun decodeGeneric(memo: String, signature: String): String {
    try {
        val functionSelector = if (memo.startsWith("0x")) {
            memo.substring(
                2,
                10
            )
        } else {
            memo.take(8)
        }

        val (functionName, paramTypes) = parseSignature(signature)

        val abi = buildAbiJson(
            functionSelector,
            functionName,
            paramTypes
        )

        val call = memo.toHexByteArray()
        val decoded = wallet.core.jni.EthereumAbi.decodeCall(
            call,
            abi
        )

        return decoded
    } catch (e: Exception) {
        return "Error decoding: ${e.message}"
    }
}


private fun parseSignature(signature: String): Pair<String, List<String>> {
    val openParenIndex = signature.indexOf('(')
    if (openParenIndex == -1) {
        throw IllegalArgumentException("Invalid signature format")
    }

    val functionName = signature.take(openParenIndex)
    val paramsString = signature.substring(
        openParenIndex + 1,
        signature.lastIndexOf(')')
    )

    val paramTypes = parseParameters(paramsString)

    return Pair(
        functionName,
        paramTypes
    )
}

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

private fun buildAbiJson(selector: String, functionName: String, paramTypes: List<String>): String {
    val inputs = paramTypes.mapIndexed { index, type ->
        buildInputJson(
            "param$index",
            type
        )
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


private fun buildInputJson(name: String, type: String): String {
    return if (type.startsWith("(")) {
        val tupleContent = type.substring(
            1,
            type.lastIndexOf(')')
        )
        val isArray = type.endsWith("[]")
        val baseType = if (isArray) "tuple[]" else "tuple"

        val components = parseParameters(tupleContent)
        val componentsJson = components.mapIndexed { index, componentType ->
            buildInputJson(
                "field$index",
                componentType
            )
        }.joinToString(",")

        """
            {
                "name": "$name",
                "type": "$baseType",
                "components": [$componentsJson]
            }
            """.trimIndent()
    } else {
        """
            {
                "name": "$name",
                "type": "$type"
            }
            """.trimIndent()
    }
}