package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

sealed class CacaoUnstakeMaturity {
    data object Mature : CacaoUnstakeMaturity()

    data class Locked(val remainingBlocks: Long) : CacaoUnstakeMaturity() {
        val remainingSeconds: Long
            get() = remainingBlocks * MAYA_BLOCK_TIME_SECONDS
    }

    data object Unknown : CacaoUnstakeMaturity()

    companion object {
        // Maya block time is fixed at 6s; CACAOPOOLDEPOSITMATURITYBLOCKS is the value
        // we read live from mimir (changed from 7d to 21d in 2026).
        const val MAYA_BLOCK_TIME_SECONDS: Long = 6L
    }
}

interface GetCacaoUnstakeMaturityUseCase : suspend (String) -> CacaoUnstakeMaturity

internal class GetCacaoUnstakeMaturityUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : GetCacaoUnstakeMaturityUseCase {
    override suspend fun invoke(address: String): CacaoUnstakeMaturity = supervisorScope {
        try {
            val latestBlock = async { mayaChainApi.getLatestBlock() }
            val mayaConstants = async { mayaChainApi.getMayaConstants() }
            val cacaoProvider = async { mayaChainApi.getCacaoProvider(address) }

            val currentBlockHeight = latestBlock.await().block.header.height.toLong()
            val depositMaturity = mayaConstants.await().getValue(CACAO_POOL_DEPOSIT_MATURITY_BLOCKS)
            val lastDepositHeight = cacaoProvider.await().lastDepositHeight

            val unlockBlock = lastDepositHeight + depositMaturity
            val remainingBlocks = unlockBlock - currentBlockHeight
            if (remainingBlocks <= 0L) CacaoUnstakeMaturity.Mature
            else CacaoUnstakeMaturity.Locked(remainingBlocks)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            CacaoUnstakeMaturity.Unknown
        }
    }

    companion object {
        private const val CACAO_POOL_DEPOSIT_MATURITY_BLOCKS = "CACAOPOOLDEPOSITMATURITYBLOCKS"
    }
}
