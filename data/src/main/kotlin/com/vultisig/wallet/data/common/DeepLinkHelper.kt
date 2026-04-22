package com.vultisig.wallet.data.common

import android.net.Uri
import com.vultisig.wallet.data.models.TssAction

/** Parses Vultisig deep-link and QR-code URIs into their component parameters. */
class DeepLinkHelper(input: String) {

    /** Constructs a helper from an Android [Uri]. */
    constructor(url: Uri) : this(url.toString())

    private val scheme: String
    private val parameters: Map<String, String>

    /** Returns the raw value of the given query [key], or null if absent. */
    fun getParameter(key: String): String? {
        return parameters[key]
    }

    init {
        val parts = runCatching { input.split("?") }.getOrElse { listOf(input) }

        scheme = parts[0]
        parameters =
            runCatching { parts[1].split("&") }
                .getOrElse { emptyList() }
                .associate {
                    val pair = it.split("=", limit = 2)
                    if (pair.size == 2) {
                        pair[0] to Uri.decode(pair[1])
                    } else {
                        pair[0] to ""
                    }
                }
    }

    /** Returns the `jsonData` query parameter, or null if absent. */
    fun getJsonData(): String? {
        return parameters["jsonData"]
    }

    /** Returns the `resharePrefix` query parameter, or null if absent. */
    fun getResharePrefix(): String? {
        return parameters["resharePrefix"]
    }

    /** Returns the `assetChain` query parameter, or null if absent. */
    fun getAssetChain(): String? {
        return parameters["assetChain"]
    }

    /** Returns the `assetTicker` query parameter, or null if absent. */
    fun getAssetTicker(): String? {
        return parameters["assetTicker"]
    }

    /** Returns the `toAddress` query parameter, or null if absent. */
    fun getToAddress(): String? {
        return parameters["toAddress"]
    }

    /** Returns the `amount` query parameter, or null if absent. */
    fun getAmount(): String? {
        return parameters["amount"]
    }

    /** Returns the `memo` query parameter, or null if absent. */
    fun getMemo(): String? {
        return parameters["memo"]
    }

    /** Returns true when the URI is a `vultisig://send` deep-link. */
    fun isSendDeeplink(): Boolean {
        return scheme.equals("vultisig://send", ignoreCase = true)
    }

    /** Returns true when the URI contains a `resharePrefix` parameter. */
    fun hasResharePrefix(): Boolean {
        return parameters.containsKey("resharePrefix")
    }

    /** Returns the `type` parameter used to identify the QR flow, or null if absent. */
    fun getFlowType(): String? {
        return parameters["type"]
    }

    /** Returns the [TssAction] indicated by the `tssType` parameter, or null if unrecognized. */
    fun getTssAction(): TssAction? {
        parameters["tssType"]?.let {
            when (it.uppercase()) {
                "KEYGEN" -> return TssAction.KEYGEN
                "RESHARE" -> return TssAction.ReShare
                "MIGRATE" -> return TssAction.Migrate
                "KEYIMPORT" -> return TssAction.KeyImport
                "SINGLEKEYGEN" -> return TssAction.SingleKeygen
                else -> return null
            }
        }
        return null
    }

    companion object {

        /**
         * Builds a `vultisig://send` deep-link URI from the provided asset and recipient details.
         */
        fun createSendDeeplink(
            assetChain: String,
            assetTicker: String,
            toAddress: String,
            amount: String? = null,
            memo: String? = null,
        ): String {
            return StringBuilder()
                .apply {
                    append("vultisig://send?")
                    append("assetChain=").append(Uri.encode(assetChain))
                    append("&assetTicker=").append(Uri.encode(assetTicker))
                    append("&toAddress=").append(Uri.encode(toAddress))

                    amount?.let { append("&amount=").append(Uri.encode(it)) }

                    memo?.let { append("&memo=").append(Uri.encode(it)) }
                }
                .toString()
        }
    }
}
