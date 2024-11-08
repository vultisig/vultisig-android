package com.vultisig.wallet.data.api.errors

sealed class SwapException(message: String) : Exception(message) {
    class SwapIsNotSupported(message: String) : SwapException(message)
    class AmountCannotBeZero(message: String) : SwapException(message)
    class SameAssets(message: String) : SwapException(message)
    class UnkownSwapError(message: String) : SwapException(message)
    class InsufficentSwapAmount(message: String) : SwapException(message)

    companion object {
        fun handleSwapException(error: String): SwapException {
            with(error.lowercase()) {
                return when {
                    contains("amount cannot be zero") -> AmountCannotBeZero(error)
                    contains("swap is not supported") -> SwapIsNotSupported(error)
                    contains("/fromamount must pass") -> AmountCannotBeZero(error)
                    contains("not enough asset to pay for fees") -> InsufficentSwapAmount(error)
                    else -> UnkownSwapError(error)
                }
            }
        }
    }
}