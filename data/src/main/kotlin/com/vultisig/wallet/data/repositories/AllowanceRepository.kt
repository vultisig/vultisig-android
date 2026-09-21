package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigInteger
import javax.inject.Inject

/**
 * What the ERC-20 approval ahead of a transfer has to do; see
 * [AllowanceRepository.getApprovalRequirement].
 */
enum class ApprovalRequirement {
    /** Approval doesn't apply, or the current allowance already covers the amount. */
    NotRequired,
    /** A single `approve(spender, amount)`. */
    Approve,
    /**
     * `approve(spender, 0)` before `approve(spender, amount)`: the token rejects a non-zero ->
     * non-zero approve while a stale allowance remains (USDT and the tokens that copy it).
     */
    ResetThenApprove,
}

interface AllowanceRepository {

    /**
     * Returns `null` only when approval doesn't apply (native token / non-EVM chain); a failed RPC
     * read throws instead, so "not needed" can't be confused with "couldn't check" (#5424).
     */
    suspend fun getAllowance(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
    ): BigInteger?

    /**
     * The approval a transfer of [amount] from [srcAddress] by [dstAddress] needs. When the current
     * allowance is non-zero but too small, `approve(dstAddress, amount)` is simulated from
     * [srcAddress]: a revert means the token wants the allowance reset to zero first, so every
     * signer has to send that leg too. No token list decides this — the simulation is the only
     * signal. A failed read or simulation throws, as [getAllowance] does, rather than settling
     * either way on a guess.
     */
    suspend fun getApprovalRequirement(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
        amount: BigInteger,
    ): ApprovalRequirement
}

internal class AllowanceRepositoryImpl
@Inject
constructor(private val evmApiFactory: EvmApiFactory) : AllowanceRepository {

    override suspend fun getAllowance(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
    ): BigInteger? =
        if (contractAddress.isEmpty() || chain.standard != TokenStandard.EVM) null
        else
            evmApiFactory
                .createEvmApi(chain)
                .getAllowance(
                    contractAddress = contractAddress,
                    owner = srcAddress,
                    spender = dstAddress,
                )

    override suspend fun getApprovalRequirement(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
        amount: BigInteger,
    ): ApprovalRequirement {
        val allowance =
            getAllowance(chain, contractAddress, srcAddress, dstAddress)
                ?: return ApprovalRequirement.NotRequired
        if (allowance >= amount) return ApprovalRequirement.NotRequired
        // A zero allowance never trips the non-zero -> non-zero guard, so only a stale partial one
        // is worth a simulation.
        if (allowance.signum() == 0) return ApprovalRequirement.Approve
        val reverts =
            evmApiFactory
                .createEvmApi(chain)
                .doesErc20ApproveRevert(
                    contractAddress = contractAddress,
                    owner = srcAddress,
                    spender = dstAddress,
                    amount = amount,
                )
        return if (reverts) ApprovalRequirement.ResetThenApprove else ApprovalRequirement.Approve
    }
}
