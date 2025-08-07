package com.vultisig.wallet.data.api.errors

sealed class SwapException(message: String) : Exception(message) {
    class SwapIsNotSupported(message: String) : SwapException(message)
    class AmountCannotBeZero(message: String) : SwapException(message)
    class SameAssets(message: String) : SwapException(message)
    class UnkownSwapError(message: String) : SwapException(message)
    class InsufficentSwapAmount(message: String) : SwapException(message)
    class SwapRouteNotAvailable(message: String) : SwapException(message)
    class TimeOut(message: String) : SwapException(message)
    class NetworkConnection(message: String) : SwapException(message)
    class SmallSwapAmount(message: String) : SwapException(message)
    class InsufficientFunds(message: String) : SwapException(message)


    companion object {
        fun handleSwapException(error: String): SwapException {
            with(error.lowercase()) {
                return when {
                    contains("amount cannot be zero") -> AmountCannotBeZero(error)
                    contains("swap is not supported") -> SwapIsNotSupported(error)
                    contains("/fromamount must pass") -> AmountCannotBeZero(error)
                    contains("not enough asset to pay for fees") -> InsufficentSwapAmount(error)
                    contains("outbound amount does not meet requirements") -> InsufficentSwapAmount(error)
                    contains("failed to simulate swap: pool") -> SwapRouteNotAvailable(error)
                    contains("insufficient funds") -> InsufficientFunds(error)
                    contains("No available quotes for the requested") -> SwapRouteNotAvailable(error)
                    contains("amount less than dust threshold: invalid request") -> SmallSwapAmount(error)
                    contains("pool does not exist") -> SwapRouteNotAvailable(error)
                    contains("trading is halted") -> SwapRouteNotAvailable(error)
                    contains("timeout") -> TimeOut(error)
                    contains("unable to resolve host") -> NetworkConnection(error)
                    else -> UnkownSwapError(error)
                }
            }
        }
    }
}