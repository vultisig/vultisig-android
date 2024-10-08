package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.chains.helpers.THORChainSwaps

internal interface DepositMemo {

    sealed interface Bond : DepositMemo {
        data class Maya(
            val nodeAddress: String,
            val lpUnits: Int?,
            val assets: String,
            val providerAddress: String?,
        ) : Bond {

            override fun toString(): String = buildString {
                append("BOND")
                append(":")
                append(assets)
                append(":")
                append(lpUnits ?: "")
                append(":")
                append(nodeAddress)
                if (!providerAddress.isNullOrBlank()) {
                    append(":")
                    append(providerAddress)
                }
            }

        }

        data class Thor(
            val nodeAddress: String,
            val providerAddress: String?,
            val operatorFee: Int?,
        ) : Bond {

            override fun toString(): String = buildString {
                append("BOND:")
                append(nodeAddress)
                if (!providerAddress.isNullOrBlank()) {
                    append(":")
                    append(providerAddress)
                }
                if (operatorFee != null) {
                    append(":")
                    append(operatorFee)
                }
            }

        }
    }

    sealed interface Unbond : DepositMemo {

        data class Maya(
            val nodeAddress: String,
            val providerAddress: String?,
            val assets: String,
            val lpUnits: Int?,
        ) : Unbond {

            override fun toString(): String = buildString {
                append("UNBOND")
                append(":")
                append(assets)
                append(":")
                append(lpUnits ?: "")
                append(":")
                append(nodeAddress)
                if (!providerAddress.isNullOrBlank()) {
                    append(":")
                    append(providerAddress)
                }
            }

        }

        data class Thor(
            val nodeAddress: String,
            val srcTokenValue: TokenValue,
            val providerAddress: String?,
        ) : Unbond {

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

    }

    data class Leave(
        val nodeAddress: String,
    ) : DepositMemo {

        override fun toString(): String = buildString {
            append("LEAVE:")
            append(nodeAddress)
        }

    }

    data object DepositPool : DepositMemo {

        override fun toString(): String = "POOL+"

    }

    data class WithdrawPool(
        val basisPoints: Int,
    ) : DepositMemo {

        override fun toString(): String = buildString {
            append("POOL-:")
            append(basisPoints)
            append(":")
            append(THORChainSwaps.AFFILIATE_FEE_ADDRESS)
            append(":")
            append(THORChainSwaps.AFFILIATE_FEE_RATE)
        }

    }


    data class Custom(
        val memo: String,
    ) : DepositMemo {
        override fun toString(): String = memo
    }

}