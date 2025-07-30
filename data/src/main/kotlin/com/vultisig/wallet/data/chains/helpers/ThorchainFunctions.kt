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
        denom: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }

        val executePayload = JSONObject().apply {
            put("deposit", JSONObject())
            put("affiliate", listOf(AFFILIATE_CONTRACT, 10))
        }

        val jsonString = executePayload.toString()
        val base64EncodedMsg = Base64.encode(jsonString.toByteArray(Charsets.UTF_8))

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = base64EncodedMsg,
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                )
            ),
        )
    }

    fun sellYToken(
        fromAddress: String,
        stakingContract: String,
        slippage: String,
        denom: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }

        val executePayload = JSONObject().apply {
            put("withdraw", JSONObject().apply {
                put("slippage", slippage)
            })
        }

        val jsonString = executePayload.toString()
        val base64EncodedMsg = Base64.encode(jsonString.toByteArray(Charsets.UTF_8))

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = base64EncodedMsg,
            coins = listOf(
                CosmosCoin(
                    denom = denom,
                ),
            ),
        )
    }
}

private const val AFFILIATE_CONTRACT =
    "sthor1m4pk0kyc5xln5uznsur0d6frlvteghs0v6fyt8pw4vxfhfgskzts2g8ln6"