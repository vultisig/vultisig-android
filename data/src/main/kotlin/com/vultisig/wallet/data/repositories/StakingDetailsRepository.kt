package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.db.dao.StakingDetailsDao
import com.vultisig.wallet.data.db.mappers.toDomainModel
import com.vultisig.wallet.data.db.mappers.toDomainModels
import com.vultisig.wallet.data.db.mappers.toEntity
import javax.inject.Inject
import javax.inject.Singleton

interface StakingDetailsRepository {
    suspend fun getStakingDetails(vaultId: String): List<StakingDetails>
    
    suspend fun getStakingDetailsByCoindId(vaultId: String, coinId: String): StakingDetails?

    suspend fun getStakingDetailsById(vaultId: String, id: String): StakingDetails?
    
    suspend fun saveStakingDetails(vaultId: String, stakingDetails: StakingDetails)
    
    suspend fun saveAllStakingDetails(vaultId: String, stakingDetailsList: List<StakingDetails>)
    
    suspend fun updateStakingDetails(vaultId: String, stakingDetails: StakingDetails)
    
    suspend fun deleteStakingDetails(vaultId: String)
    
    suspend fun deleteStakingDetails(vaultId: String, coinId: String)
}

@Singleton
internal class StakingDetailsRepositoryImpl @Inject constructor(
    private val stakingDetailsDao: StakingDetailsDao,
) : StakingDetailsRepository {

    override suspend fun getStakingDetails(vaultId: String): List<StakingDetails> {
        return stakingDetailsDao.getAllByVaultIdSuspend(vaultId)
            .toDomainModels()
    }

    override suspend fun getStakingDetailsByCoindId(vaultId: String, coinId: String): StakingDetails? {
        return stakingDetailsDao.getByVaultIdAndCoinId(vaultId, coinId)
            ?.toDomainModel()
    }

    override suspend fun getStakingDetailsById(
        vaultId: String,
        id: String
    ): StakingDetails? {
        TODO("Not yet implemented")
    }

    override suspend fun saveStakingDetails(vaultId: String, stakingDetails: StakingDetails) {
        stakingDetailsDao.insert(stakingDetails.toEntity(vaultId))
    }

    override suspend fun saveAllStakingDetails(vaultId: String, stakingDetailsList: List<StakingDetails>) {
        stakingDetailsDao.insertAll(stakingDetailsList.map { it.toEntity(vaultId) })
    }

    override suspend fun updateStakingDetails(vaultId: String, stakingDetails: StakingDetails) {
        stakingDetailsDao.update(stakingDetails.toEntity(vaultId))
    }

    override suspend fun deleteStakingDetails(vaultId: String) {
        stakingDetailsDao.deleteAllByVaultId(vaultId)
    }

    override suspend fun deleteStakingDetails(vaultId: String, coinId: String) {
        stakingDetailsDao.deleteByVaultIdAndCoinId(vaultId, coinId)
    }
}