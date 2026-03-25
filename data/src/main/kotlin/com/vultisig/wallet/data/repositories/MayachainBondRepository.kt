package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaMidgardHealth
import com.vultisig.wallet.data.api.MayaMidgardNetworkData
import com.vultisig.wallet.data.api.MayaNodeInfo
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.utils.SimpleCache
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

interface MayachainBondRepository {
    suspend fun getAllNodes(): List<MayaNodeInfo>

    suspend fun getNodeDetails(nodeAddress: String): MayaNodeInfo

    suspend fun getMidgardNetworkData(): MayaMidgardNetworkData

    suspend fun getMidgardHealthData(): MayaMidgardHealth

    suspend fun getMayaNodePools(): List<MayaNodePool>

    suspend fun getBondableAssets(): List<String>

    suspend fun getLpBondableAssets(address: String): List<String>

    suspend fun clearCache()
}

@Singleton
class MayachainBondRepositoryImpl @Inject constructor(private val mayaChainApi: MayaChainApi) :
    MayachainBondRepository {

    companion object {
        private const val MIDGARD_NETWORK_KEY = "maya_midgard_network"
        private const val MIDGARD_HEALTH_KEY = "maya_midgard_health"
    }

    private val midgardNetworkCache = SimpleCache<String, MayaMidgardNetworkData>()
    private val midgardHealthCache = SimpleCache<String, MayaMidgardHealth>()

    override suspend fun getAllNodes(): List<MayaNodeInfo> {
        return try {
            mayaChainApi.getAllNodes()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching all Maya nodes")
            throw e
        }
    }

    override suspend fun getNodeDetails(nodeAddress: String): MayaNodeInfo {
        return try {
            mayaChainApi.getNodeDetails(nodeAddress)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya node details for: $nodeAddress")
            throw e
        }
    }

    override suspend fun getMidgardNetworkData(): MayaMidgardNetworkData {
        return try {
            midgardNetworkCache.getOrPut(MIDGARD_NETWORK_KEY) {
                Timber.d("Fetching Maya Midgard network data from API")
                mayaChainApi.getMidgardNetworkData()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya Midgard network data")
            throw e
        }
    }

    override suspend fun getMidgardHealthData(): MayaMidgardHealth {
        return try {
            midgardHealthCache.getOrPut(MIDGARD_HEALTH_KEY) {
                Timber.d("Fetching Maya Midgard health data from API")
                mayaChainApi.getMidgardHealth()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya Midgard health data")
            throw e
        }
    }

    override suspend fun getMayaNodePools(): List<MayaNodePool> {
        return try {
            mayaChainApi.getMayaNodePools().filter { it.status == "Available" }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Maya node pools")
            throw e
        }
    }

    override suspend fun getBondableAssets(): List<String> {
        return try {
            getMayaNodePools().filter { it.bondable }.map { it.asset }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching bondable Maya assets")
            throw e
        }
    }

    override suspend fun getLpBondableAssets(address: String): List<String> {
        return try {
            val bondableAssets = getBondableAssets().toSet()
            val lpPools = mayaChainApi.getMemberDetails(address).pools.map { it.pool }.toSet()
            bondableAssets.intersect(lpPools).toList()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching LP bondable assets for address: $address")
            throw e
        }
    }

    override suspend fun clearCache() {
        Timber.d("Clearing all MayachainBond caches")
        midgardNetworkCache.clear()
        midgardHealthCache.clear()
    }
}
