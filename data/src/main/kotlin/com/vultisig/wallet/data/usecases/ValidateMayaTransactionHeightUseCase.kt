package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

private const val CACAO_POOL_DEPOSIT_MATURITY_BLOCKS = "CACAOPOOLDEPOSITMATURITYBLOCKS"
private const val MAYA_BLOCK_TIME_SECONDS = 6L

interface ValidateMayaTransactionHeightUseCase : suspend (String) -> Boolean

internal class ValidateMayaTransactionHeightUseCaseImpl
@Inject
constructor(private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase) :
    ValidateMayaTransactionHeightUseCase {
    override suspend fun invoke(address: String): Boolean =
        getMayaCacaoMaturityStatus(address).isMature
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
 * fail-closed default (`isMature = false`) on RPC error so transient failures keep the CTA disabled
 * rather than letting users hit `deposit_error_has_not_reached_maturity` at submit time.
 */
interface GetMayaCacaoMaturityStatusUseCase : suspend (String) -> MayaCacaoMaturityStatus

internal class GetMayaCacaoMaturityStatusUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : GetMayaCacaoMaturityStatusUseCase {
    override suspend fun invoke(address: String): MayaCacaoMaturityStatus = coroutineScope {
        try {
            val latestBlock = async { mayaChainApi.getLatestBlock() }
            val mayaConstants = async { mayaChainApi.getMayaConstants() }
            val cacaoProvider = async { mayaChainApi.getCacaoProvider(address) }

            val currentBlockHeight = latestBlock.await().block.header.height.toLong()
            val depositMaturity =
                mayaConstants.await()[CACAO_POOL_DEPOSIT_MATURITY_BLOCKS]
                    ?: error("missing $CACAO_POOL_DEPOSIT_MATURITY_BLOCKS mimir")
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
            MayaCacaoMaturityStatus(isMature = false, remainingBlocks = 0, remainingSeconds = 0)
        }
    }
}
