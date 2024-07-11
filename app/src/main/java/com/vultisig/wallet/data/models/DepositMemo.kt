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

    data class Unbond(
        val nodeAddress: String,
        val srcTokenValue: TokenValue,
        val providerAddress: String?,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("UNBOND:")
            append(nodeAddress)
            append(":")
            append(srcTokenValue.value)
            if (!providerAddress.isNullOrBlank()) {
                append(":")
                append(providerAddress)
            }
        }

    }

    data class Leave(
        val nodeAddress: String,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("LEAVE:")
            append(nodeAddress)
        }

    }

    data class Custom(
        val memo: String,
    ) : DepositMemo() {

        override fun toString(): String = memo

    }

}