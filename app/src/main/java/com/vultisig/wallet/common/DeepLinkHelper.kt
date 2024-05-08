package com.vultisig.wallet.common

import com.vultisig.wallet.models.TssAction

class DeepLinkHelper(private val input: String) {
    private val scheme: String
    private val parameters: Map<String, String>

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

    fun getFlowType(): String? {
        return parameters["type"]
    }

    fun getTssAction(): TssAction? {
        parameters["tssType"]?.let {
            when (it.uppercase()) {
                "KEYGEN" -> return TssAction.KEYGEN
                "RESHARE" -> return TssAction.ReShare
                else -> return null
            }
        }
        return null
    }

}