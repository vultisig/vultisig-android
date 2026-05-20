package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

interface ValidateMayaTransactionHeightUseCase : suspend (String) -> Boolean

internal class ValidateMayaTransactionHeightUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : ValidateMayaTransactionHeightUseCase {
    override suspend fun invoke(address: String): Boolean = supervisorScope {
        try {
            val latestBlock = async { mayaChainApi.getLatestBlock() }
            val mayaConstants = async { mayaChainApi.getMayaConstants() }
            val cacaoProvider = async { mayaChainApi.getCacaoProvider(address) }

            val currentBlockHeight = latestBlock.await().block.header.height.toLong()
            val depositMaturity = mayaConstants.await().getValue(CACAO_POOL_DEPOSIT_MATURITY_BLOCKS)
            val lastDepositHeight = cacaoProvider.await().lastDepositHeight

            currentBlockHeight - lastDepositHeight > depositMaturity
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            false
        }
    }

    companion object {
        private const val CACAO_POOL_DEPOSIT_MATURITY_BLOCKS = "CACAOPOOLDEPOSITMATURITYBLOCKS"
    }
}

/**
 * Snapshot of a CACAO pool position's maturity, as read live from MAYA chain state. Used by the
 * Unstake CACAO surface to gate the CTA on the on-chain maturity flag (rather than a hardcoded
 * window) and to render the remaining-time countdown for users still in the lock period.
 *
 * @property isMature true when the position's `lastDepositHeight` plus the chain's
 *   `CACAOPOOLDEPOSITMATURITYBLOCKS` mimir has been reached.
 * @property remainingBlocks blocks left until maturity; clamped at 0 once mature.
 * @property remainingSeconds wall-clock seconds left, computed from [remainingBlocks] at the MAYA
 *   block time of 6s/block.
 */
data class MayaCacaoMaturityStatus(
    val isMature: Boolean,
    val remainingBlocks: Long,
    val remainingSeconds: Long,
)

/**
 * Reads the on-chain CACAO pool maturity status for [address] so the Unstake UI can gate the CTA
 * and render an accurate countdown instead of guessing against a hardcoded 7-day window. Returns a
 * permissive default (`isMature = true`, no remaining time) on failure so transient RPC errors
 * don't trap users — the submit-time guard in [ValidateMayaTransactionHeightUseCase] still enforces
 * chain truth before broadcast.
 */
interface GetMayaCacaoMaturityStatusUseCase : suspend (String) -> MayaCacaoMaturityStatus

internal class GetMayaCacaoMaturityStatusUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : GetMayaCacaoMaturityStatusUseCase {
    override suspend fun invoke(address: String): MayaCacaoMaturityStatus = supervisorScope {
        try {
            val latestBlock = async { mayaChainApi.getLatestBlock() }
            val mayaConstants = async { mayaChainApi.getMayaConstants() }
            val cacaoProvider = async { mayaChainApi.getCacaoProvider(address) }

            val currentBlockHeight = latestBlock.await().block.header.height.toLong()
            val depositMaturity = mayaConstants.await().getValue(CACAO_POOL_DEPOSIT_MATURITY_BLOCKS)
            val lastDepositHeight = cacaoProvider.await().lastDepositHeight

            val remainingBlocks =
                (lastDepositHeight + depositMaturity - currentBlockHeight).coerceAtLeast(0)
            MayaCacaoMaturityStatus(
                isMature = remainingBlocks == 0L,
                remainingBlocks = remainingBlocks,
                remainingSeconds = remainingBlocks * MAYA_BLOCK_TIME_SECONDS,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to read CACAO maturity status")
            MayaCacaoMaturityStatus(isMature = true, remainingBlocks = 0, remainingSeconds = 0)
        }
    }

    companion object {
        private const val CACAO_POOL_DEPOSIT_MATURITY_BLOCKS = "CACAOPOOLDEPOSITMATURITYBLOCKS"
        private const val MAYA_BLOCK_TIME_SECONDS = 6L
    }
}
