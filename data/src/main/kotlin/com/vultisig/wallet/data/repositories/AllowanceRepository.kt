package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigInteger
import javax.inject.Inject

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
}
