package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.json.JSONArray
import org.json.JSONObject
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.WasmExecuteContractPayload
import wallet.core.jni.Base64

/** Builders for CosmWasm execute-contract payloads and THORChain transaction memos. */
object ThorchainFunctions {

    /**
     * Builds the CosmWasm payload to bond (stake) RUJI tokens.
     *
     * @param fromAddress sender address
     * @param stakingContract RUJI staking contract address
     * @param denom token denomination
     * @param amount amount in base units
     */
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
            executeMsg = ExecMsg.accountBond(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

    /**
     * Builds the CosmWasm payload to withdraw (unstake) RUJI tokens.
     *
     * @param fromAddress sender address
     * @param amount amount to withdraw, as a string
     * @param stakingContract RUJI staking contract address
     */
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
            executeMsg = ExecMsg.accountWithdraw(amount),
            coins = listOf(),
        )
    }

    /**
     * Builds the CosmWasm payload to claim pending RUJI staking rewards.
     *
     * @param fromAddress sender address
     * @param stakingContract RUJI staking contract address
     */
    fun claimRujiRewards(fromAddress: String, stakingContract: String): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = ExecMsg.accountClaim(),
            coins = listOf(),
        )
    }

    /**
     * Builds the CosmWasm payload to mint (deposit) yTokens via a vault contract.
     *
     * @param fromAddress sender address
     * @param stakingContract vault/staking contract address
     * @param tokenContract yToken contract address
     * @param denom token denomination to deposit
     * @param amount amount in base units
     */
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

        val depositMsg = JSONObject().apply { put(KEY_DEPOSIT, JSONObject()) }
        val base64Msg = Base64.encode(depositMsg.toString().toByteArray(Charsets.UTF_8))

        val fullPayload =
            JSONObject().apply {
                put(
                    KEY_EXECUTE,
                    JSONObject().apply {
                        put(KEY_CONTRACT_ADDR, tokenContract)
                        put(KEY_MSG, base64Msg)
                        put(
                            KEY_AFFILIATE,
                            JSONArray().apply {
                                put(VULTISIG_AFFILIATE_ADDRESS)
                                put(10)
                            },
                        )
                    },
                )
            }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = fullPayload.toString(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

    /**
     * Builds the CosmWasm payload to redeem (burn) yTokens and withdraw the underlying asset.
     *
     * @param fromAddress sender address
     * @param tokenContract yToken contract address
     * @param slippage maximum acceptable slippage
     * @param denom yToken denomination
     * @param amount amount to redeem in base units
     */
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

        val executePayload =
            JSONObject().apply {
                put(KEY_WITHDRAW, JSONObject().apply { put(KEY_SLIPPAGE, slippage) })
            }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = tokenContract,
            executeMsg = executePayload.toString(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

    /**
     * Builds the CosmWasm payload to bond (stake) TCY tokens via the liquid staking contract.
     *
     * @param fromAddress sender address
     * @param stakingContract TCY liquid staking contract address
     * @param denom token denomination
     * @param amount amount in base units
     */
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
            executeMsg = ExecMsg.liquidBond(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

    /**
     * Builds the CosmWasm payload to unbond TCY units from the liquid staking contract.
     *
     * @param units number of LP units to unbond
     * @param stakingContract TCY liquid staking contract address
     * @param fromAddress sender address
     */
    fun unStakeTcyCompound(
        units: BigInteger,
        stakingContract: String,
        fromAddress: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(units >= BigInteger.ONE) { "units cannot be lower than 1" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = ExecMsg.liquidUnbond(),
            coins = listOf(CosmosCoin(denom = Memo.DENOM_STAKING_TCY, amount = units.toString())),
        )
    }

    /**
     * Builds the THORChain memo for claiming RUJI staking rewards.
     *
     * @param contractAddress the RUJI staking contract address
     * @param tokenAmountInt the amount of tokens to claim
     */
    fun rujiRewardsMemo(contractAddress: String, tokenAmountInt: BigInteger): String {
        require(contractAddress.isNotBlank()) { "contractAddress cannot be blank" }
        require(tokenAmountInt >= BigInteger.ZERO) { "tokenAmountInt cannot be negative" }
        return "${Memo.CLAIM_PREFIX}$contractAddress:$tokenAmountInt"
    }

    /**
     * Builds the THORChain memo for unstaking TCY tokens.
     *
     * @param basisPoints percentage of the position to unstake, in basis points (0–10000)
     */
    fun tcyUnstakeMemo(basisPoints: Int): String {
        require(basisPoints in 0..10_000) { "basisPoints must be between 0 and 10000" }
        return "${Memo.TCY_UNSTAKE_PREFIX}$basisPoints"
    }
}

private object Memo {
    const val CLAIM_PREFIX = "claim:"
    const val TCY_UNSTAKE_PREFIX = "TCY-:"
    const val DENOM_STAKING_TCY = "x/staking-tcy"
}

private object ExecMsg {
    fun accountBond() = """{ "account": { "bond": {} } }"""

    fun accountWithdraw(amount: String) =
        """{ "account": { "withdraw": { "amount": "$amount" } } }"""

    fun accountClaim() = """{ "account": { "claim": {} } }"""

    fun liquidBond() = """{ "liquid": { "bond": {} } }"""

    fun liquidUnbond() = """{ "liquid": { "unbond": {} } }"""
}

private const val KEY_EXECUTE = "execute"
private const val KEY_CONTRACT_ADDR = "contract_addr"
private const val KEY_MSG = "msg"
private const val KEY_AFFILIATE = "affiliate"
private const val KEY_DEPOSIT = "deposit"
private const val KEY_WITHDRAW = "withdraw"
private const val KEY_SLIPPAGE = "slippage"

private const val VULTISIG_AFFILIATE_ADDRESS = "thor1svfwxevnxtm4ltnw92hrqpqk4vzuzw9a4jzy04"
