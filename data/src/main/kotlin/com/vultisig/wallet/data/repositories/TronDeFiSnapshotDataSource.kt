package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Last-known account + resource state for a TRON address, persisted so the DeFi positions screen
 * can render immediately on open and refresh in the background instead of flashing skeletons. See
 * issue #4568.
 */
@Serializable
data class TronDeFiSnapshot(val account: TronAccountJson, val resource: TronAccountResourceJson)

interface TronDeFiSnapshotDataSource {

    suspend fun read(tronAddress: String): TronDeFiSnapshot?

    suspend fun write(tronAddress: String, snapshot: TronDeFiSnapshot)
}

internal class TronDeFiSnapshotDataSourceImpl
@Inject
constructor(private val appDataStore: AppDataStore, private val json: Json) :
    TronDeFiSnapshotDataSource {

    override suspend fun read(tronAddress: String): TronDeFiSnapshot? {
        val stored = appDataStore.readData(keyFor(tronAddress)).first() ?: return null
        return try {
            json.decodeFromString<TronDeFiSnapshot>(stored)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode cached TRON DeFi snapshot")
            null
        }
    }

    override suspend fun write(tronAddress: String, snapshot: TronDeFiSnapshot) {
        appDataStore.set(keyFor(tronAddress), json.encodeToString(snapshot))
    }

    // Prefixed so it never collides with TronResourceDataSource, which keys on the bare address.
    private fun keyFor(tronAddress: String) = stringPreferencesKey("tron_defi_account_$tronAddress")
}
