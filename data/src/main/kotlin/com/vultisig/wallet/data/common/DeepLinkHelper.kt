package com.vultisig.wallet.data.common

import android.net.Uri
import com.vultisig.wallet.data.models.TssAction

class DeepLinkHelper(input: String) {

    constructor(url: Uri) : this(url.toString())

    private val scheme: String
    private val parameters: Map<String, String>

    fun getParameter(key: String): String? {
        return parameters[key]
    }

    init {
        val parts = runCatching {
            input.split("?")
        }.getOrElse { listOf(input) }

        scheme = parts[0]
        parameters = runCatching { parts[1].split("&") }.getOrElse { emptyList() }.associate {
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

    fun getAssetChain(): String? {
        return parameters["assetChain"]
    }

    fun getAssetTicker(): String? {
        return parameters["assetTicker"]
    }

    fun getToAddress(): String? {
        return parameters["toAddress"]
    }

    fun getAmount(): String? {
        return parameters["amount"]
    }

    fun getMemo(): String? {
        return parameters["memo"]
    }

    fun isSendDeeplink(): Boolean {
        return scheme.equals("vultisig://send", ignoreCase = true)
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

    fun createSendDeeplink(
        assetChain: String,
        assetTicker: String,
        toAddress: String,
        amount: String? = null,
        memo: String? = null
    ): String {
        return StringBuilder().apply {
            append("vultisig://send?")
            append("assetChain=").append(assetChain)
            append("&assetTicker=").append(assetTicker)
            append("&toAddress=").append(toAddress)

            amount?.let {
                append("&amount=").append(it)
            }

            memo?.let {
                append("&memo=").append(it)
            }
        }.toString()
    }

}