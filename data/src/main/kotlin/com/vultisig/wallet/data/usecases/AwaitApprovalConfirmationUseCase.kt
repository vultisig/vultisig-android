package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.blockTimeMs
import javax.inject.Inject
import kotlinx.coroutines.delay
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
        repeat(MAX_BLOCKS) { attempt ->
            when (evmApi.getTxStatus(txHash)?.result?.status) {
                RECEIPT_SUCCESS -> return ApprovalConfirmationResult.Confirmed
                RECEIPT_FAILURE -> return ApprovalConfirmationResult.Failed
                else -> {
                    Timber.d(
                        "Approval %s not yet confirmed, attempt %d/%d",
                        txHash,
                        attempt + 1,
                        MAX_BLOCKS,
                    )
                    delay(chain.blockTimeMs)
                }
            }
        }

        return ApprovalConfirmationResult.TimedOut
    }

    private companion object {
        const val RECEIPT_SUCCESS = "0x1"
        const val RECEIPT_FAILURE = "0x0"
        const val MAX_BLOCKS = 5
    }
}
