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
 * @property isUnknown true when the live read failed; lets the UI render an explicit "Couldn't
 *   verify position" hint instead of a silent disabled button.
 */
data class MayaCacaoMaturityStatus(
    val isMature: Boolean,
    val remainingBlocks: Long,
    val remainingSeconds: Long,
    val isUnknown: Boolean = false,
) {
    companion object {
        val UNKNOWN: MayaCacaoMaturityStatus =
            MayaCacaoMaturityStatus(
                isMature = false,
                remainingBlocks = 0L,
                remainingSeconds = 0L,
                isUnknown = true,
            )
    }
}

/**
 * Reads the on-chain CACAO pool maturity status for [address] so the Unstake UI can gate the CTA
 * and render an accurate countdown instead of guessing against a hardcoded 7-day window. Returns
 * [MayaCacaoMaturityStatus.UNKNOWN] on RPC error so transient failures keep the CTA disabled (the
 * legacy [ValidateMayaTransactionHeightUseCase] wrapper still reads `isMature = false`) while
 * letting the UI distinguish "couldn't verify" from "still locked".
 */
interface GetMayaCacaoMaturityStatusUseCase : suspend (String) -> MayaCacaoMaturityStatus

internal class GetMayaCacaoMaturityStatusUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : GetMayaCacaoMaturityStatusUseCase {
    override suspend fun invoke(address: String): MayaCacaoMaturityStatus =
        try {
            coroutineScope {
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
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to read CACAO maturity status")
            MayaCacaoMaturityStatus.UNKNOWN
        }
}
