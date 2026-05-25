package com.vultisig.wallet.data.api.errors

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Pins [SwapKitError.fromCode] for every documented SwapKit V3 error code. Drift here flips a
 * recognised upstream error into a generic Network/Server fallback and the user sees the wrong
 * localized message — so every code in the iOS reference must round-trip to the expected variant.
 */
class SwapKitErrorFromCodeTest {

    @Test
    fun `fromCode maps every documented SwapKit error code to its typed variant`() {
        assertInstanceOf(
            SwapKitError.ApiKeyMissing::class.java,
            SwapKitError.fromCode("apiKeyMissing"),
        )
        assertInstanceOf(
            SwapKitError.ApiKeyInvalid::class.java,
            SwapKitError.fromCode("apiKeyInvalid"),
        )
        assertInstanceOf(
            SwapKitError.InsufficientBalance::class.java,
            SwapKitError.fromCode("insufficientBalance"),
        )
        assertInstanceOf(
            SwapKitError.InsufficientAllowance::class.java,
            SwapKitError.fromCode("insufficientAllowance"),
        )
        assertInstanceOf(
            SwapKitError.UnableToBuildTransaction::class.java,
            SwapKitError.fromCode("unableToBuildTransaction"),
        )
        assertInstanceOf(
            SwapKitError.SwapRouteNotFound::class.java,
            SwapKitError.fromCode("swapRouteNotFound"),
        )
        assertInstanceOf(
            SwapKitError.QuoteDeviation::class.java,
            SwapKitError.fromCode("outputAmountDeviationTooHigh"),
        )
        assertInstanceOf(SwapKitError.NoRoutes::class.java, SwapKitError.fromCode("noRoutesFound"))
        assertInstanceOf(
            SwapKitError.BlackListAsset::class.java,
            SwapKitError.fromCode("blackListAsset"),
        )
        assertInstanceOf(
            SwapKitError.InvalidSourceAddress::class.java,
            SwapKitError.fromCode("invalidSourceAddress"),
        )
        assertInstanceOf(
            SwapKitError.InvalidDestinationAddress::class.java,
            SwapKitError.fromCode("invalidDestinationAddress"),
        )
        assertInstanceOf(
            SwapKitError.AddressScreening::class.java,
            SwapKitError.fromCode("isSanctionedAddress"),
        )
        assertInstanceOf(
            SwapKitError.AddressScreening::class.java,
            SwapKitError.fromCode("addressScreeningFailed"),
        )
    }

    @Test
    fun `fromCode does not map providerNotEnabled or routeFiltered from the wire`() {
        // iOS produces these client-side (provider cache + route filter), not from the wire
        // envelope. Mirror that on Android: unknown codes fall through to Network so the wire
        // can never silently steal those variants from the client-side gates.
        assertInstanceOf(
            SwapKitError.Network::class.java,
            SwapKitError.fromCode("providerNotEnabled"),
        )
        assertInstanceOf(SwapKitError.Network::class.java, SwapKitError.fromCode("routeFiltered"))
    }

    @Test
    fun `fromCode aliases failedToRetrieveBalance to UnableToBuildTransaction`() {
        // NEAR Intents collapses upstream UTXO-indexer failures into `failedToRetrieveBalance`.
        // The user-facing meaning is the same as `unableToBuildTransaction`: try another provider.
        assertInstanceOf(
            SwapKitError.UnableToBuildTransaction::class.java,
            SwapKitError.fromCode("failedToRetrieveBalance"),
        )
    }

    @Test
    fun `fromCode is case-insensitive on the wire code`() {
        assertInstanceOf(SwapKitError.NoRoutes::class.java, SwapKitError.fromCode("NOROUTESFOUND"))
        assertInstanceOf(
            SwapKitError.AddressScreening::class.java,
            SwapKitError.fromCode("isSanctionedAddress".uppercase()),
        )
    }

    @Test
    fun `fromCode normalizes case under Turkish locale`() {
        // Pins the Locale.ROOT call in fromCode. Without it, the default Turkish locale's
        // `I` → `ı` (dotless i) mapping would lowercase `ISSANCTIONEDADDRESS` to
        // `ıssanctionedaddress`, miss the `issanctionedaddress` arm, and fall through to Network.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertInstanceOf(
                SwapKitError.AddressScreening::class.java,
                SwapKitError.fromCode("ISSANCTIONEDADDRESS"),
            )
            assertInstanceOf(
                SwapKitError.NoRoutes::class.java,
                SwapKitError.fromCode("NOROUTESFOUND"),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `fromCode falls through to Server when HTTP status is supplied and code is unknown`() {
        val error =
            SwapKitError.fromCode("someBrandNewCode", fallbackMessage = "body", httpStatus = 503)
        val server = assertInstanceOf(SwapKitError.Server::class.java, error)
        assertEquals(503, server.httpStatus)
        assertEquals("body", server.message)
    }

    @Test
    fun `fromCode falls through to Network when no HTTP status is supplied and code is unknown`() {
        val error = SwapKitError.fromCode("someBrandNewCode")
        assertInstanceOf(SwapKitError.Network::class.java, error)
    }

    @Test
    fun `fromCode tolerates leading and trailing whitespace on the code`() {
        assertInstanceOf(
            SwapKitError.NoRoutes::class.java,
            SwapKitError.fromCode("  noRoutesFound  "),
        )
    }

    @Test
    fun `fromCode accepts snake_case spellings for every typed code`() {
        // SwapKit fixtures in the repo disagree (camelCase vs snake_case), so fromCode strips
        // underscores during normalization to keep both spellings on the typed path rather than
        // dropping the snake_case variant to the generic Network/Server fallback.
        assertInstanceOf(
            SwapKitError.InsufficientBalance::class.java,
            SwapKitError.fromCode("insufficient_balance"),
        )
        assertInstanceOf(
            SwapKitError.InsufficientAllowance::class.java,
            SwapKitError.fromCode("insufficient_allowance"),
        )
        assertInstanceOf(
            SwapKitError.SwapRouteNotFound::class.java,
            SwapKitError.fromCode("swap_route_not_found"),
        )
        assertInstanceOf(
            SwapKitError.NoRoutes::class.java,
            SwapKitError.fromCode("no_routes_found"),
        )
        assertInstanceOf(SwapKitError.NoRoutes::class.java, SwapKitError.fromCode("no_routes"))
        assertInstanceOf(
            SwapKitError.QuoteDeviation::class.java,
            SwapKitError.fromCode("output_amount_deviation_too_high"),
        )
        assertInstanceOf(
            SwapKitError.AddressScreening::class.java,
            SwapKitError.fromCode("is_sanctioned_address"),
        )
    }

    @Test
    fun `fromCode returns Network for a null code`() {
        // Defensive — the http extractor returns null when the body is blank or has no `code`
        // field; that path must not crash and must classify as transport-level.
        assertInstanceOf(SwapKitError.Network::class.java, SwapKitError.fromCode(null))
    }
}
