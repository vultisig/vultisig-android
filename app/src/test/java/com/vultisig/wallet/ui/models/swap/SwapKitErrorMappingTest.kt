package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [SwapQuoteManager.mapSwapKitErrorToFormError] for every [SwapKitError] variant — this is the
 * user-visible payload of the SwapKit Phase 1 localization wiring. Drift here surfaces the wrong
 * `swapkit_error_*` string at runtime, and the exhaustive `when` compiles regardless.
 */
internal class SwapKitErrorMappingTest {

    private val manager =
        SwapQuoteManager(
            swapQuoteRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            convertTokenValueToFiat = mockk(relaxed = true),
            mapTokenValueToDecimalUiString = mockk(relaxed = true),
            fiatValueToString = mockk(relaxed = true),
            searchToken = mockk(relaxed = true),
            convertTokenToTokenUseCase = mockk(relaxed = true),
        )

    @Test
    fun `stateless variants map to their string resources`() {
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_api_key_missing),
            manager.mapSwapKitErrorToFormError(SwapKitError.ApiKeyMissing),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_api_key_invalid),
            manager.mapSwapKitErrorToFormError(SwapKitError.ApiKeyInvalid),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_insufficient_balance),
            manager.mapSwapKitErrorToFormError(SwapKitError.InsufficientBalance),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_insufficient_allowance),
            manager.mapSwapKitErrorToFormError(SwapKitError.InsufficientAllowance),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_unable_to_build_transaction),
            manager.mapSwapKitErrorToFormError(SwapKitError.UnableToBuildTransaction),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_swap_route_not_found),
            manager.mapSwapKitErrorToFormError(SwapKitError.SwapRouteNotFound),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_no_routes_found),
            manager.mapSwapKitErrorToFormError(SwapKitError.NoRoutes()),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_black_list_asset),
            manager.mapSwapKitErrorToFormError(SwapKitError.BlackListAsset),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_invalid_source_address),
            manager.mapSwapKitErrorToFormError(SwapKitError.InvalidSourceAddress),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_invalid_destination_address),
            manager.mapSwapKitErrorToFormError(SwapKitError.InvalidDestinationAddress),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_address_screening),
            manager.mapSwapKitErrorToFormError(SwapKitError.AddressScreening),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_provider_not_enabled),
            manager.mapSwapKitErrorToFormError(SwapKitError.ProviderNotEnabled),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_route_filtered),
            manager.mapSwapKitErrorToFormError(SwapKitError.RouteFiltered),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_network),
            manager.mapSwapKitErrorToFormError(SwapKitError.Network("offline")),
        )
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_decoding),
            manager.mapSwapKitErrorToFormError(SwapKitError.Decoding("decode failed")),
        )
    }

    @Test
    fun `QuoteDeviation routes through FormattedText so 5%% unescapes to 5%`() {
        // The empty args list is intentional — FormattedText runs String.format which collapses
        // the resource's `%%` escape back to a literal `%`. A StringResource here would render
        // `5%%` verbatim because it skips String.format entirely.
        assertEquals(
            UiText.FormattedText(
                R.string.swapkit_error_output_amount_deviation_too_high,
                emptyList(),
            ),
            manager.mapSwapKitErrorToFormError(SwapKitError.QuoteDeviation()),
        )
    }

    @Test
    fun `UnsupportedTxType formats the wire txType into the message`() {
        assertEquals(
            UiText.FormattedText(R.string.swapkit_error_unsupported_tx_type, listOf("PSBT")),
            manager.mapSwapKitErrorToFormError(SwapKitError.UnsupportedTxType("PSBT")),
        )
    }

    @Test
    fun `MalformedAmount with raw value formats the raw into the message`() {
        assertEquals(
            UiText.FormattedText(R.string.swapkit_error_malformed_amount, listOf("42.5")),
            manager.mapSwapKitErrorToFormError(SwapKitError.MalformedAmount("42.5")),
        )
    }

    @Test
    fun `MalformedAmount with blank raw falls back to generic decoding copy`() {
        // SwapKitQuoteSource passes empty raw when the upstream amount is null; the developer
        // sentinel must not leak into the user-facing form error.
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_decoding),
            manager.mapSwapKitErrorToFormError(SwapKitError.MalformedAmount("")),
        )
    }

    @Test
    fun `Server with HTTP status formats the status into the message`() {
        assertEquals(
            UiText.FormattedText(R.string.swapkit_error_server, listOf(503)),
            manager.mapSwapKitErrorToFormError(SwapKitError.Server(503, "Service Unavailable")),
        )
    }

    @Test
    fun `Server with null HTTP status falls back to the generic network copy`() {
        // A null httpStatus rendered as `0` reads like a real status — fall back to the network
        // copy. fromCode never emits null, but Server can be constructed directly elsewhere.
        assertEquals(
            UiText.StringResource(R.string.swapkit_error_network),
            manager.mapSwapKitErrorToFormError(SwapKitError.Server(null, "unknown")),
        )
    }
}
