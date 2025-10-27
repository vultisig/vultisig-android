package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BondedNodesResponse
import com.vultisig.wallet.data.api.ChurnEntry
import com.vultisig.wallet.data.api.MidgardNetworkData
import com.vultisig.wallet.data.api.NodeDetailsResponse
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.utils.SimpleCache
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface ThorchainBondRepository {
    suspend fun getBondedNodes(address: String): BondedNodesResponse?
    suspend fun getNodeDetails(nodeAddress: String): NodeDetailsResponse?
    suspend fun getChurns(): List<ChurnEntry>
    suspend fun getChurnInterval(): Long
    suspend fun getMidgardNetworkData(): MidgardNetworkData?
    suspend fun clearCache()
}

@Singleton
class ThorchainBondRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
) : ThorchainBondRepository {

    companion object {
        // Cache keys
        private const val MIDGARD_NETWORK_KEY = "midgard_network"
        private const val CHURN_INTERVAL_KEY = "churn_interval"
        private const val CHURNS_KEY = "churns"
    }

    // Caches for different data types
    private val midgardNetworkCache = SimpleCache<String, MidgardNetworkData>()
    private val churnIntervalCache = SimpleCache<String, Long>()
    private val churnsCache = SimpleCache<String, List<ChurnEntry>>()

    override suspend fun getBondedNodes(address: String): BondedNodesResponse {
        return try {
            thorChainApi.getBondedNodes(address)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching bonded nodes for address: $address")
            throw e
        }
    }

    override suspend fun getNodeDetails(nodeAddress: String): NodeDetailsResponse? {
        return try {
            thorChainApi.getNodeDetails(nodeAddress)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching node details for: $nodeAddress")
            throw e
        }
    }

    override suspend fun getChurns(): List<ChurnEntry> {
        return try {
            churnsCache.getOrPut(CHURNS_KEY) {
                Timber.d("Fetching churns from API")
                thorChainApi.getChurns()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching churns")
            throw e
        }
    }

    override suspend fun getChurnInterval(): Long {
        return try {
            churnIntervalCache.getOrPut(CHURN_INTERVAL_KEY) {
                Timber.d("Fetching churn interval from API")
                thorChainApi.getChurnInterval()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching churn interval")
            throw e
        }
    }

    override suspend fun getMidgardNetworkData(): MidgardNetworkData? {
        return try {
            midgardNetworkCache.getOrPut(MIDGARD_NETWORK_KEY) {
                Timber.d("Fetching Midgard network data from API")
                thorChainApi.getMidgardNetworkData()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Midgard network data")
            throw e
        }
    }

    override suspend fun clearCache() {
        Timber.d("Clearing all ThorchainBond caches")
        midgardNetworkCache.clear()
        churnIntervalCache.clear()
        churnsCache.clear()
    }
}
