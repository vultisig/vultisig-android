package com.vultisig.wallet.data.common

import com.vultisig.wallet.data.models.TssAction

class DeepLinkHelper(input: String) {
    private val scheme: String
    private val parameters: Map<String, String>

    /**
     * Retrieve a query parameter value by key.
     *
     * @param key The parameter name to look up.
     * @return The parameter value, or `null` if the key is not present.
     */
    fun getParameter(key: String): String? {
        return parameters[key]
    }

    init {
        val parts = input.split("?")
        scheme = parts[0]
        parameters = parts[1].split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }
    }

    fun getJsonData(): String? {
        return parameters["jsonData"]
    }

    fun getResharePrefix(): String? {
        return parameters["resharePrefix"]
    }

    fun hasResharePrefix(): Boolean {
        return parameters.containsKey("resharePrefix")
    }

    fun getFlowType(): String? {
        return parameters["type"]
    }

    fun getTssAction(): TssAction? {
        parameters["tssType"]?.let {
            when (it.uppercase()) {
                "KEYGEN" -> return TssAction.KEYGEN
                "RESHARE" -> return TssAction.ReShare
                "MIGRATE" -> return TssAction.Migrate
                else -> return null
            }
        }
        return null
    }

}