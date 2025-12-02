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

    data object Stake : DepositMemo {
        override fun toString(): String = "d"
    }

    data object Unstake : DepositMemo {
        override fun toString(): String = "w"
    }


    data class Custom(
        val memo: String,
    ) : DepositMemo {
        override fun toString(): String = memo
    }

    data class TransferIbc(
        val srcChain: Chain,
        val dstChain: Chain,
        val dstAddress: String,

        val memo: String?,
    ) : DepositMemo {
        override fun toString(): String =
            buildString {
                append("${dstChain.raw}:${ibcChannel}:${dstAddress}")
                if (!memo.isNullOrBlank()) {
                    append(":$memo")
                }
            }

        private val ibcChannel: String = when (srcChain) {
            Chain.Kujira -> when (dstChain) {
                Chain.GaiaChain -> "channel-0"
                Chain.Akash -> "channel-64"
                Chain.Dydx -> "channel-118"
                Chain.Noble -> "channel-62"
                Chain.Osmosis -> "channel-3"
                else -> ""
            }

            Chain.Osmosis -> when (dstChain) {
                Chain.GaiaChain -> "channel-0"
                else -> ""
            }

            Chain.GaiaChain -> when (dstChain) {
                Chain.Kujira -> "channel-343"
                Chain.Osmosis -> "channel-141"
                Chain.Noble -> "channel-536"
                Chain.Akash -> "channel-184"
                else -> ""
            }

            else -> ""
        }
    }


}