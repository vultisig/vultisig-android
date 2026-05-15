package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.blockTimeMs
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

sealed interface ApprovalConfirmationResult {
    data object Confirmed : ApprovalConfirmationResult

    data object TimedOut : ApprovalConfirmationResult

    data object Failed : ApprovalConfirmationResult
}

fun interface AwaitApprovalConfirmationUseCase {
    suspend operator fun invoke(chain: Chain, txHash: String): ApprovalConfirmationResult
}

internal class AwaitApprovalConfirmationUseCaseImpl
@Inject
constructor(private val evmApiFactory: EvmApiFactory) : AwaitApprovalConfirmationUseCase {

    override suspend fun invoke(chain: Chain, txHash: String): ApprovalConfirmationResult {
        val evmApi = evmApiFactory.createEvmApi(chain)
        val pollDelayMs = chain.blockTimeMs.coerceAtLeast(MIN_POLL_DELAY_MS)
        return withTimeoutOrNull(TIMEOUT_MS) { pollUntilTerminal(evmApi, txHash, pollDelayMs) }
            ?: ApprovalConfirmationResult.TimedOut
    }

    // EvmApi.getTxStatus catches Exception broadly to map RPC failures to null (treated as
    // "pending"). That broad catch also swallows CancellationException, so the loop needs an
    // explicit ensureActive() between polls to honour structured concurrency.
    private suspend fun pollUntilTerminal(
        evmApi: EvmApi,
        txHash: String,
        pollDelayMs: Long,
    ): ApprovalConfirmationResult {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            when (evmApi.getTxStatus(txHash)?.result?.status) {
                RECEIPT_SUCCESS -> return ApprovalConfirmationResult.Confirmed
                RECEIPT_FAILURE -> return ApprovalConfirmationResult.Failed
                else -> {
                    attempt++
                    Timber.d("Approval %s pending, attempt %d", txHash, attempt)
                    delay(pollDelayMs)
                }
            }
        }
    }

    private companion object {
        const val RECEIPT_SUCCESS = "0x1"
        const val RECEIPT_FAILURE = "0x0"
        // Bounded wall-clock budget per swap approval. Long enough for Ethereum congestion,
        // short enough to fail the keysign flow before the user gives up. Matches the original
        // PR design before the block-count refactor.
        const val TIMEOUT_MS = 120_000L
        // Don't hammer fast L2 RPCs (Arbitrum block time is 1s).
        const val MIN_POLL_DELAY_MS = 2_000L
    }
}
