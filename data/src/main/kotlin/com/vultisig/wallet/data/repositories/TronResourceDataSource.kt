package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.sources.AppDataStore
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

interface TronResourceDataSource {

    suspend fun readTronResourceLimit(
        tronAddress: String,
    ): ResourceUsage?


    suspend fun setTronResourceLimit(
        tronAddress: String,
        tronResourceReository: ResourceUsage
    )
}

internal class TronResourceDataSourceImpl @Inject constructor(
    private val appDataSource: AppDataStore,
    private val json: Json,
) : TronResourceDataSource {
    override suspend fun readTronResourceLimit(
        tronAddress: String
    ): ResourceUsage? {
        val tronResourceString: String? = appDataSource.readData(stringPreferencesKey(tronAddress)).first()

        return tronResourceString?.let { json.decodeFromString<ResourceUsage>(it) }
    }

    override suspend fun setTronResourceLimit(
        tronAddress: String,
        tronResourceReository: ResourceUsage
    ) {
        appDataSource.set(
            stringPreferencesKey(tronAddress),
            json.encodeToString(
                tronResourceReository
            )
        )
    }

}