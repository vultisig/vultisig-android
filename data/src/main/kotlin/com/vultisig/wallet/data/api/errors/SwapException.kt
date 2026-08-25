package com.vultisig.wallet.data.api.errors

@Suppress("SerialVersionUIDInSerializableClass")
sealed class SwapException(message: String) : Exception(message) {
    @Suppress("SerialVersionUIDInSerializableClass")
    class SwapIsNotSupported(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class AmountCannotBeZero(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class SameAssets(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class UnkownSwapError(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class InsufficentSwapAmount(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class SwapRouteNotAvailable(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class TradingHalted(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class TimeOut(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class NetworkConnection(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class SmallSwapAmount(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class InsufficientFunds(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class HighPriceImpact(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class RateLimitExceeded(message: String) : SwapException(message)

    @Suppress("SerialVersionUIDInSerializableClass")
    class AmountBelowDustThreshold(message: String) : SwapException(message)

    companion object {
        /**
         * Substrings THORChain and MAYAChain emit when a chain or an asset is paused upstream: a
         * protocol-wide trading halt, `global_trading_paused`, or `chain_trading_paused`. Matched
         * against the lowercased body, and kept in step with the iOS marker set so both stacks
         * classify the same node text the same way.
         */
        private val TRADING_HALT_MARKERS =
            listOf(
                "trading is halted",
                "trading halted",
                "trading paused",
                "_trading_paused",
                "is paused",
            )

        fun handleSwapException(error: String): SwapException {
            with(error.lowercase()) {
                return when {
                    // A pause is temporary and retryable, so it is matched ahead of every other
                    // class. The nodes often report it alongside a fee or amount clause ("trading
                    // paused; not enough asset to pay for fees"), and reading that as an amount
                    // problem sends the user off to adjust an amount that cannot help while
                    // trading is down.
                    TRADING_HALT_MARKERS.any { marker -> contains(marker) } -> TradingHalted(error)
                    contains("amount cannot be zero") -> AmountCannotBeZero(error)
                    contains("swap is not supported") -> SwapIsNotSupported(error)
                    contains("/fromamount must pass") -> AmountCannotBeZero(error)
                    contains("not enough asset to pay for fees") -> InsufficentSwapAmount(error)
                    contains("outbound amount does not meet requirements") ->
                        InsufficentSwapAmount(error)
                    contains("failed to simulate swap: pool") -> SwapRouteNotAvailable(error)
                    contains("failed to simulate handler: pool") -> SwapRouteNotAvailable(error)
                    // Maya's streaming path fails before the pool lookup when a chain has been
                    // delisted, reporting an internal "dev error" instead of "pool X doesn't
                    // exist". Deterministic — retrying or changing the amount can never help.
                    contains("both pools have no rune balance") -> SwapRouteNotAvailable(error)
                    contains("failed to calculate max streaming swap quantity") ->
                        SwapRouteNotAvailable(error)
                    contains("pool") && contains("doesn't exist") -> SwapRouteNotAvailable(error)
                    contains("insufficient funds") -> InsufficientFunds(error)
                    contains("no available quotes for the requested") ->
                        SwapRouteNotAvailable(error)
                    contains("amount less than dust threshold") -> AmountBelowDustThreshold(error)
                    contains("amount less than min swap amount") -> SmallSwapAmount(error)
                    contains("pool does not exist") -> SwapRouteNotAvailable(error)
                    // Thornode rejects an asset id it can't parse ("bad to asset: invalid
                    // symbol: invalid request") — deterministic, so never "adjust the amount".
                    contains("bad to asset") ||
                        contains("bad from asset") ||
                        contains("invalid symbol") -> SwapRouteNotAvailable(error)
                    contains("timeout") -> TimeOut(error)
                    contains("unable to resolve host") -> NetworkConnection(error)
                    contains("no internet connection") -> NetworkConnection(error)
                    contains("too many requests") || contains("rate limit") ->
                        RateLimitExceeded(error)
                    contains("slippage") -> HighPriceImpact(error)
                    contains("price impact") -> HighPriceImpact(error)
                    contains("exceeds desired slippage") -> HighPriceImpact(error)
                    contains("route not profitable") -> HighPriceImpact(error)
                    contains("slippage tolerance exceeded") -> HighPriceImpact(error)
                    contains("too many requests") -> RateLimitExceeded(error)
                    else -> UnkownSwapError(error)
                }
            }
        }
    }
}
