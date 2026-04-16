package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

fun interface AwaitApprovalConfirmationUseCase {
    suspend operator fun invoke(chain: Chain, txHash: String)
}

internal class AwaitApprovalConfirmationUseCaseImpl
@Inject
constructor(private val evmApiFactory: EvmApiFactory) : AwaitApprovalConfirmationUseCase {

    override suspend fun invoke(chain: Chain, txHash: String) {
        val evmApi = evmApiFactory.createEvmApi(chain)

        val confirmed = withTimeoutOrNull(MAX_WAIT_MS) { pollUntilConfirmed(evmApi, txHash) }

        if (confirmed != true) {
            error(
                "Swap is taking longer than expected. Please check your transaction history before retrying"
            )
        }
    }

    private suspend fun pollUntilConfirmed(evmApi: EvmApi, txHash: String): Boolean {
        while (true) {
            val status = evmApi.getTxStatus(txHash)?.result?.status
            when (status) {
                RECEIPT_SUCCESS -> return true
                RECEIPT_FAILURE ->
                    error("Swap could not be completed because the token approval step failed")
                else -> {
                    Timber.d(
                        "Approval %s not yet confirmed (status=%s), retrying in %ds",
                        txHash,
                        status,
                        POLL_INTERVAL_MS / 1000,
                    )
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    private companion object {
        const val RECEIPT_SUCCESS = "0x1"
        const val RECEIPT_FAILURE = "0x0"
        const val POLL_INTERVAL_MS = 4_000L
        const val MAX_WAIT_MS = 120_000L
    }
}
