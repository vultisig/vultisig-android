package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.workers.TokenRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "TokenRefreshWorker"

interface WorkerRepository {
    fun discoveryTokens(vaultId: String? = null, nativeToken: Coin? = null)
}

internal class WorkerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    val gson: Gson,
) : WorkerRepository {
    override fun discoveryTokens(vaultId: String?, nativeToken: Coin?) {
        val dataBuilder = Data.Builder()
        if (vaultId != null) {
            dataBuilder.putString(TokenRefreshWorker.VAULT_ID, vaultId)
        }
        if (nativeToken != null) {
            dataBuilder.putString(TokenRefreshWorker.NATIVE_TOKEN, gson.toJson(nativeToken))
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