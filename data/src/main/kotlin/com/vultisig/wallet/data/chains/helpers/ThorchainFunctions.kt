package com.vultisig.wallet.data.chains.helpers

import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.WasmExecuteContractPayload

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
}