package com.vultisig.wallet.data.models

internal sealed class DepositMemo {

    data class Bond(
        val nodeAddress: String,
        val providerAddress: String?,
        val operatorFee: TokenValue?,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("BOND:")
            append(nodeAddress)
            if (!providerAddress.isNullOrBlank()) {
                append(":")
                append(providerAddress)
            }
            if (operatorFee != null) {
                append(":")
                append(operatorFee.value)
            }
        }

    }

}