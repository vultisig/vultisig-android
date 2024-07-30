package com.vultisig.wallet.data.api.errors

internal sealed class SwapError(message: String): Exception(message){
    class SwapIsNotSupported(message: String): SwapError(message)
    class AmountCannotBeZero(message: String): SwapError(message)
    class SameAssets(message: String): SwapError(message)

    companion object {
        fun handleSwapError(error: String?) {
            if (error.isNullOrBlank()) return
            with(error.lowercase()){
                when  {
                    contains("amount cannot be zero") -> throw AmountCannotBeZero(error)
                    contains("swap is not supported") -> throw SwapIsNotSupported(error)
                    else -> {}
                }
            }
        }
    }
}