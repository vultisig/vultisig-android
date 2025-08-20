package com.vultisig.wallet.data.chains.helpers

import org.json.JSONArray
import org.json.JSONObject
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.WasmExecuteContractPayload
import java.math.BigInteger
import wallet.core.jni.Base64

object ThorchainFunctions {

    fun stakeRUJI(
        fromAddress: String,
        stakingContract: String,
        denom: String,
        amount: BigInteger,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = """{ "account": { "bond": {} } }""",
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                    amount = amount.toString(),
                )
            )
        )
    }

    fun unstakeRUJI(
        fromAddress: String,
        amount: String,
        stakingContract: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "fromAddress Cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract Cannot be empty" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = """{ "account": { "withdraw": { "amount": "$amount" } } }""",
            coins = listOf()
        )
    }

    fun claimRujiRewards(
        fromAddress: String,
        stakingContract: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = """{ "account": { "claim": {} } }""",
            coins = listOf(),
        )
    }

    fun mintYToken(
        fromAddress: String,
        stakingContract: String,
        tokenContract: String,
        denom: String,
        amount: BigInteger,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(tokenContract.isNotEmpty()) { "tokenContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }

        val depositMsg = JSONObject().apply {
            put("deposit", JSONObject())
        }
        val base64Msg = Base64.encode(depositMsg.toString().toByteArray(Charsets.UTF_8))

        val fullPayload = JSONObject().apply {
            put("execute", JSONObject().apply {
                put("contract_addr", tokenContract)
                put("msg", base64Msg)
                put("affiliate", JSONArray().apply {
                    put(VULTISIG_AFFILIATE_ADDRESS)
                    put(10)
                })
            })
        }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = fullPayload.toString(),
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                    amount = amount.toString(),
                )
            ),
        )
    }

    fun redeemYToken(
        fromAddress: String,
        tokenContract: String,
        slippage: String,
        denom: String,
        amount: BigInteger,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(tokenContract.isNotEmpty()) { "tokenContract cannot be empty" }
        require(slippage.isNotEmpty()) { "slippage cannot be empty" }
        require(denom.isNotEmpty()) { "denom cannot be empty" }

        val executePayload = JSONObject().apply {
            put("withdraw", JSONObject().apply {
                put("slippage", slippage)
            })
        }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = tokenContract,
            executeMsg = executePayload.toString(),
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                    amount = amount.toString(),
                ),
            ),
        )
    }

    fun stakeTcyCompound(
        fromAddress: String,
        stakingContract: String,
        denom: String,
        amount: BigInteger,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = """{ "liquid": { "bond": {} } }""",
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                    amount = amount.toString(),
                )
            ),
        )
    }

    fun unStakeTcyCompound(
        units: Int,
        stakingContract: String,
        fromAddress: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(units >= 1) { "units cannot be lower than 1" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = """{ "liquid": { "unbond": {} } }""",
            coins = listOf(
                CosmosCoin(
                    denom = "x/staking-tcy",
                    amount = units.toString(),
                )
            )
        )
    }
}

private const val VULTISIG_AFFILIATE_ADDRESS =
    "thor1svfwxevnxtm4ltnw92hrqpqk4vzuzw9a4jzy04"