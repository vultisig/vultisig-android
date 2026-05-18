package com.vultisig.wallet.data.usecases

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vultisig.wallet.data.workers.TokenRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "TokenRefreshWorker"

interface DiscoverTokenUseCase : (String?, String?) -> Unit

internal class DiscoverTokenUseCaseImpl
@Inject
constructor(@ApplicationContext private val context: Context) : DiscoverTokenUseCase {

    override fun invoke(vaultId: String?, chainId: String?) {
        val data =
            Data.Builder()
                .apply {
                    vaultId?.takeIf(String::isNotBlank)?.let {
                        putString(TokenRefreshWorker.ARG_VAULT_ID, it)
                    }
                    chainId?.takeIf(String::isNotBlank)?.let {
                        putString(TokenRefreshWorker.ARG_CHAIN, it)
                    }
                }
                .build()

        val request =
            OneTimeWorkRequestBuilder<TokenRefreshWorker>().setInputData(data).addTag(TAG).build()

        // Unique name is scoped to (vault, chain) so concurrent enqueues for different
        // scopes don't cancel each other (e.g. a full-vault scan on save vs. per-chain
        // scans on chain-enable). REPLACE still collapses repeated calls for the same
        // scope into the latest request.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                uniqueWorkName(vaultId, chainId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }

    private fun uniqueWorkName(vaultId: String?, chainId: String?): String =
        "$TAG:${vaultId.orEmpty()}:${chainId.orEmpty()}"
}
