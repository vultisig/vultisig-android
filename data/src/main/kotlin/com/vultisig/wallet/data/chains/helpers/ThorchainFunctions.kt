package com.vultisig.wallet.data.chains.helpers

import org.json.JSONObject
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.WasmExecuteContractPayload
import wallet.core.jni.Base64

object ThorchainFunctions {

    fun stakeRUJI(
        fromAddress: String,
        stakingContract: String,
        denom: String,
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

    fun receiveYToken(
        fromAddress: String,
        stakingContract: String,
        tokenContract: String,
        denom: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }

        val depositMsg = JSONObject().apply {
            put("deposit", JSONObject())
        }
        val base64Msg = Base64.encode(depositMsg.toString().toByteArray(Charsets.UTF_8))
        val fullPayload = JSONObject().apply {
            put("execute", JSONObject().apply {
                put("contract_addr", tokenContract)
                put("msg", base64Msg)
                put("affiliate", listOf(VULTISIG_AFFILIATE_ADDRESS, 10))
            })
        }

        val executeMsgPayload = Base64.encode(fullPayload.toString().toByteArray(Charsets.UTF_8))

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = executeMsgPayload,
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                )
            ),
        )
    }

    fun sellYToken(
        fromAddress: String,
        tokenContract: String,
        slippage: String,
        denom: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(tokenContract.isNotEmpty()) { "tokenContract cannot be empty" }

        val executePayload = JSONObject().apply {
            put("withdraw", JSONObject().apply {
                put("slippage", slippage)
            })
        }

        val jsonString = executePayload.toString()
        val base64EncodedMsg = Base64.encode(jsonString.toByteArray(Charsets.UTF_8))

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = tokenContract,
            executeMsg = base64EncodedMsg,
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                ),
            ),
        )
    }
}

private const val VULTISIG_AFFILIATE_ADDRESS =
    "thor1svfwxevnxtm4ltnw92hrqpqk4vzuzw9a4jzy04"