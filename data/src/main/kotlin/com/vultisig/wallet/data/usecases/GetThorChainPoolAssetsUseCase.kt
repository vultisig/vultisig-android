package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import javax.inject.Inject

/** Pool status thornode reports for a pool that can be traded — and set as a preferred asset. */
private const val STATUS_AVAILABLE = "available"

interface GetThorChainPoolAssetsUseCase {
    /**
     * The assets of every available THORChain pool, resolved to wallet coins and ordered by ticker.
     *
     * Staged and suspended pools are dropped: thornode rejects a THORName preferred asset whose
     * pool is not available, so offering one could only produce a transaction that fails on chain.
     */
    suspend operator fun invoke(): List<ThorChainPoolCoin>
}

internal class GetThorChainPoolAssetsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainPoolAssetsUseCase {

    override suspend fun invoke(): List<ThorChainPoolCoin> =
        thorChainApi
            .getPools()
            .asSequence()
            .filter { it.status.equals(STATUS_AVAILABLE, ignoreCase = true) }
            .mapNotNull { ThorChainPoolCoin.from(asset = it.asset, decimals = it.decimals) }
            .sortedBy { it.coin.ticker }
            .toList()
}
