package com.vultisig.wallet.data.repositories.vault

import com.vultisig.wallet.data.db.dao.VaultMetadataDao
import com.vultisig.wallet.data.db.models.VaultMetadataEntity
import com.vultisig.wallet.data.models.VaultId
import javax.inject.Inject

interface VaultMetadataRepo {

    suspend fun shouldVerifyServerBackup(vaultId: VaultId): Boolean

    suspend fun setServerBackupVerified(vaultId: VaultId)

    suspend fun requireServerBackupVerification(vaultId: VaultId)

}

internal class VaultMetadataRepoImpl @Inject constructor(
    private val dao: VaultMetadataDao,
) : VaultMetadataRepo {

    override suspend fun shouldVerifyServerBackup(vaultId: VaultId): Boolean {
        val should = vaultId !in shouldVerifyRequiredInSession &&
                dao.getBy(vaultId)?.isServerBackupVerified == false
        shouldVerifyRequiredInSession += vaultId
        return should
    }


    override suspend fun setServerBackupVerified(vaultId: VaultId) {
        dao.upsert(getOrDefault(vaultId).copy(isServerBackupVerified = true))
    }

    override suspend fun requireServerBackupVerification(vaultId: VaultId) {
        dao.upsert(getOrDefault(vaultId).copy(isServerBackupVerified = false))
    }

    private suspend fun getOrDefault(vaultId: VaultId) =
        dao.getBy(vaultId) ?: VaultMetadataEntity(vaultId, false)

    companion object {
        private val shouldVerifyRequiredInSession = mutableSetOf<VaultId>()
    }

}