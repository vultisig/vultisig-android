package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.WasmExecuteContractPayload
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
            executeMsg = ExecMsg.accountBond(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
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
            executeMsg = ExecMsg.accountWithdraw(amount),
            coins = listOf(),
        )
    }

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

        val base64Msg = Base64.encode("""{"$KEY_DEPOSIT":{}}""".toByteArray(Charsets.UTF_8))
        val executeMsg =
            """{"$KEY_EXECUTE":{"$KEY_CONTRACT_ADDR":"$tokenContract","$KEY_MSG":"$base64Msg","$KEY_AFFILIATE":["$VULTISIG_AFFILIATE_ADDRESS",10]}}"""

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = executeMsg,
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
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

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = tokenContract,
            executeMsg = """{"$KEY_WITHDRAW":{"$KEY_SLIPPAGE":"$slippage"}}""",
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
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
            executeMsg = ExecMsg.liquidBond(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

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
     * Stakes into the auto-compounding RUJI position, which mints sRUJI receipt shares. This is the
     * `liquid.bond` sibling of [stakeRUJI]'s `account.bond` — the same contract, a different
     * position — and is funded with the bond token (`x/ruji`), like the bonded side.
     */
    fun stakeRujiCompound(
        fromAddress: String,
        stakingContract: String,
        denom: String,
        amount: BigInteger,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(denom.isNotEmpty()) { "Denom cannot be empty" }
        require(amount >= BigInteger.ONE) { "amount cannot be lower than 1" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = ExecMsg.liquidBond(),
            coins = listOf(CosmosCoin(denom = denom, amount = amount.toString())),
        )
    }

    /**
     * Redeems from the auto-compounding RUJI position. Unlike [unstakeRUJI], which names an amount
     * of bonded RUJI, this is funded with sRUJI receipt *shares* — the caller must convert a
     * RUJI-denominated amount at the pool's share price before calling.
     *
     * @param shares the sRUJI receipt shares to redeem, in base units
     */
    fun unstakeRujiCompound(
        shares: BigInteger,
        stakingContract: String,
        fromAddress: String,
    ): WasmExecuteContractPayload {
        require(fromAddress.isNotEmpty()) { "FromAddress cannot be empty" }
        require(stakingContract.isNotEmpty()) { "stakingContract cannot be empty" }
        require(shares >= BigInteger.ONE) { "shares cannot be lower than 1" }

        return WasmExecuteContractPayload(
            senderAddress = fromAddress,
            contractAddress = stakingContract,
            executeMsg = ExecMsg.liquidUnbond(),
            coins = listOf(CosmosCoin(denom = Memo.DENOM_STAKING_RUJI, amount = shares.toString())),
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
    const val DENOM_STAKING_RUJI = "x/staking-x/ruji"
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
