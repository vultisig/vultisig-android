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

    data object DepositPool : DepositMemo() {

        override fun toString(): String = "POOL+"

    }

    data class WithdrawPool(
        val basisPoints: Int,
        val affiliate: String?,
        val affiliateFee: String?,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("POOL-:")
            append(basisPoints)
            if (!affiliate.isNullOrBlank()) {
                append(":")
                append(affiliate)
            }
            if (!affiliateFee.isNullOrBlank()) {
                append(":")
                append(affiliateFee)
            }
        }

    }

    data class Add(
        val pool: String,
        val pairedAddress: String?,
        val affiliateAddress: String?,
        val affiliateFee: TokenValue?,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("ADD:")
            append(pool)
            if (!pairedAddress.isNullOrBlank()) {
                append(":")
                append(pairedAddress)
            }
            if (!affiliateAddress.isNullOrBlank()) {
                append(":")
                append(affiliateAddress)
            }
            if (affiliateFee != null) {
                append(":")
                append(affiliateFee.value)
            }
        }

    }

    data class Withdraw(
        val pool: String,
        val points: String,
        val asset: String?,
    ) : DepositMemo() {

        override fun toString(): String = buildString {
            append("WITHDRAW:")
            append(pool)
            append(":")
            append(points)
            if (!asset.isNullOrBlank()) {
                append(":")
                append(asset)
            }
        }

    }


    data class Custom(
        val memo: String,
    ) : DepositMemo() {

        override fun toString(): String = memo

    }

}