package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.maya.MayaHealth
import com.vultisig.wallet.data.api.models.maya.MayaNetworkInfoResponse
import com.vultisig.wallet.data.utils.SimpleCache
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface MayaBondRepository {
    suspend fun getNetworkInfo(): MayaNetworkInfoResponse
    suspend fun getHealth(): MayaHealth
    suspend fun clearCache()
}

@Singleton
class MayaBondRepositoryImpl @Inject constructor(
    private val mayaChainApi: MayaChainApi,
) : MayaBondRepository {

    companion object {
        // Cache keys
        private const val NETWORK_INFO_KEY = "maya_network_info"
        private const val HEALTH_KEY = "maya_health"
    }

    // Caches for different data types
    private val networkInfoCache = SimpleCache<String, MayaNetworkInfoResponse>()
    private val healthCache = SimpleCache<String, MayaHealth>()

    override suspend fun getNetworkInfo(): MayaNetworkInfoResponse {
        return try {
            networkInfoCache.getOrPut(NETWORK_INFO_KEY) {
                Timber.d("Fetching Maya network info from API")
                mayaChainApi.getNetworkInfo()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya network info")
            throw e
        }
    }

    override suspend fun getHealth(): MayaHealth {
        return try {
            healthCache.getOrPut(HEALTH_KEY) {
                Timber.d("Fetching Maya health from API")
                mayaChainApi.getHealth()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya health")
            throw e
        }
    }

    override suspend fun clearCache() {
        Timber.d("Clearing all MayaBond caches")
        networkInfoCache.clear()
        healthCache.clear()
    }
}