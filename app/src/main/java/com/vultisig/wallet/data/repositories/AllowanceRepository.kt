package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.models.Chain
import java.math.BigInteger
import javax.inject.Inject

internal interface AllowanceRepository {

    suspend fun getAllowance(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
    ): BigInteger?

}

internal class AllowanceRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : AllowanceRepository {

    override suspend fun getAllowance(
        chain: Chain,
        contractAddress: String,
        srcAddress: String,
        dstAddress: String,
    ): BigInteger? =
        if (contractAddress.isEmpty() || chain.standard != TokenStandard.EVM) null
        else evmApiFactory.createEvmApi(chain)
            .getAllowance(
                contractAddress = contractAddress,
                owner = srcAddress,
                spender = dstAddress,
            )

}