package com.vultisig.wallet.data.usecases

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vultisig.wallet.data.workers.TokenRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "TokenRefreshWorker"

interface DiscoverTokenUseCase: (String?, String?) -> Unit

internal class DiscoverTokenUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiscoverTokenUseCase {
    override fun invoke(vaultId: String?, chainId: String?) {
        val dataBuilder = Data.Builder()
        if (!vaultId.isNullOrBlank()) {
            dataBuilder.putString(TokenRefreshWorker.ARG_VAULT_ID, vaultId)
        }
        if (!chainId.isNullOrBlank()) {
            dataBuilder.putString(TokenRefreshWorker.ARG_CHAIN, chainId)
        }
        val workData = dataBuilder.build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            TAG,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setInputData(workData)
                .setConstraints(Constraints.Builder().build())
                .addTag(TAG)
                .build()
        )
    }
}