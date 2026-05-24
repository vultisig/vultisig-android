package com.vultisig.wallet.data.api.errors

import java.util.Locale

/**
 * Typed errors surfaced by the SwapKit aggregator integration. Each variant maps 1:1 to a known
 * failure mode documented at https://docs.swapkit.dev (or a client-side filter outcome). The swap
 * UI converts each variant to a localized `swapkit_error_*` string.
 */
sealed class SwapKitError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** SwapKit proxy reports its partner API key wasn't supplied. */
    class ApiKeyMissing : SwapKitError("SwapKit API key not configured")

    /** SwapKit proxy reports its partner API key was rejected upstream. */
    class ApiKeyInvalid : SwapKitError("SwapKit API key invalid")

    /** Source-chain balance is below the route's required input. */
    class InsufficientBalance : SwapKitError("Insufficient balance for this route")

    /** ERC-20 allowance below the route's required input — user must approve first. */
    class InsufficientAllowance : SwapKitError("Insufficient token allowance")

    /**
     * Aliases `unableToBuildTransaction` and `failedToRetrieveBalance` — both surface as "this
     * route is currently unavailable" to avoid leaking the upstream indexer detail.
     */
    class UnableToBuildTransaction : SwapKitError("SwapKit could not build the transaction")

    /** The chosen `routeId` was rejected by `/v3/swap` — typically expired. */
    class SwapRouteNotFound : SwapKitError("SwapKit route not found")

    /**
     * Live `/v3/swap` price drifted further from the user's accepted `/v3/quote` than tolerated. UI
     * surfaces a "refresh quote" prompt.
     */
    class QuoteDeviation(message: String = "Output amount deviation too high") :
        SwapKitError(message)

    /** No route survived the THORChain/Maya and single-hop filters, or upstream returned none. */
    class NoRoutes(message: String = "No SwapKit route available") : SwapKitError(message)

    /** Source or destination asset is on SwapKit's deny-list. */
    class BlackListAsset : SwapKitError("Asset blocked by SwapKit aggregator")

    class InvalidSourceAddress : SwapKitError("Invalid source address")

    class InvalidDestinationAddress : SwapKitError("Invalid destination address")

    /**
     * SwapKit's OFAC/Chainalysis screening flagged the source or destination address. Aliases the
     * upstream `isSanctionedAddress` and `addressScreeningFailed` codes — they're displayed
     * identically because the user can't act on the distinction.
     */
    class AddressScreening : SwapKitError("Address screening failed")

    /** `meta.txType` is not yet wired to a per-chain signer. */
    class UnsupportedTxType(val txType: String) :
        SwapKitError("Unsupported SwapKit tx type: $txType")

    /** SwapKit-side feature flag turned off for this chain pair. */
    class ProviderNotEnabled : SwapKitError("SwapKit is not enabled on this chain")

    /** Every candidate route was dropped by client-side filtering (Thor/Maya, multi-hop). */
    class RouteFiltered : SwapKitError("All SwapKit routes were filtered out")

    /** `expectedBuyAmount` or another numeric wire field was not parseable. */
    class MalformedAmount(val raw: String) :
        SwapKitError("Malformed amount returned by SwapKit: $raw")

    /** Transport-level failure (no HTTP response received: timeout, DNS, SSL, connection reset). */
    class Network(message: String, cause: Throwable? = null) : SwapKitError(message, cause)

    /** HTTP 4xx/5xx with no recognised typed code — kept distinct from [Network]. */
    class Server(val httpStatus: Int?, message: String, cause: Throwable? = null) :
        SwapKitError(message, cause)

    /** Response shape did not deserialize against the expected V3 schema. */
    class Decoding(message: String, cause: Throwable? = null) : SwapKitError(message, cause)

    companion object {
        /**
         * Map SwapKit's documented `/v3/swap` and `/v3/quote` error codes onto a typed error.
         * Unknown codes fall through to [Server] (with HTTP status) or [Network] (without one).
         */
        fun fromCode(
            code: String?,
            fallbackMessage: String? = null,
            httpStatus: Int? = null,
        ): SwapKitError {
            val raw = code?.trim().orEmpty()
            val message = fallbackMessage ?: raw.ifEmpty { "SwapKit request failed" }
            // Locale.ROOT to dodge Turkish-locale's `I` → `ı` (dotless i) mapping; the SwapKit
            // wire codes are ASCII camelCase and must round-trip on any device locale.
            return when (raw.lowercase(Locale.ROOT)) {
                "apikeymissing" -> ApiKeyMissing()
                "apikeyinvalid" -> ApiKeyInvalid()
                "insufficientbalance" -> InsufficientBalance()
                "insufficientallowance" -> InsufficientAllowance()
                "unabletobuildtransaction",
                "failedtoretrievebalance" -> UnableToBuildTransaction()
                "swaproutenotfound" -> SwapRouteNotFound()
                "noroutesfound",
                "no_routes_found",
                "no_routes" -> NoRoutes(fallbackMessage ?: raw)
                "outputamountdeviationtoohigh",
                "output_amount_deviation_too_high" -> QuoteDeviation(fallbackMessage ?: raw)
                "blacklistasset" -> BlackListAsset()
                "invalidsourceaddress" -> InvalidSourceAddress()
                "invaliddestinationaddress" -> InvalidDestinationAddress()
                "issanctionedaddress",
                "addressscreeningfailed" -> AddressScreening()
                "providernotenabled" -> ProviderNotEnabled()
                "routefiltered" -> RouteFiltered()
                else -> if (httpStatus != null) Server(httpStatus, message) else Network(message)
            }
        }
    }
}
