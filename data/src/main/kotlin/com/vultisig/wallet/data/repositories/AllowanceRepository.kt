package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigInteger
import javax.inject.Inject

interface AllowanceRepository {

    /**
     * Returns `null` when approval doesn't apply (native token or non-EVM chain) — never for a
     * failed read. A failed RPC read throws instead, so callers can tell "no approval needed" apart
     * from "couldn't check" rather than treating the latter as an allowance of zero (#5424).
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
