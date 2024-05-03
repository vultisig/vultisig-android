package com.voltix.wallet.models

data class SignedTransactionResult(
    val rawTransaction: String,
    val transactionHash: String,
    val signature: String? = null,
) {
}