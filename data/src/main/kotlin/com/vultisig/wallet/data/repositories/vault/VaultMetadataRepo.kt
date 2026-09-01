package com.vultisig.wallet.data.repositories.vault

import com.vultisig.wallet.data.db.dao.VaultMetadataDao
import com.vultisig.wallet.data.db.models.VaultMetadataEntity
import com.vultisig.wallet.data.models.VaultId
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

interface VaultMetadataRepo {

    suspend fun isFastVaultPasswordReminderRequired(vaultId: VaultId): Boolean

    suspend fun setFastVaultPasswordReminderShownDate(vaultId: VaultId, date: LocalDate)
}

internal class VaultMetadataRepoImpl @Inject constructor(private val dao: VaultMetadataDao) :
    VaultMetadataRepo {

    override suspend fun isFastVaultPasswordReminderRequired(vaultId: VaultId): Boolean =
        getOrDefault(vaultId).fastVaultPasswordReminderShownDate.let {
            it == null ||
                (abs(ChronoUnit.DAYS.between(it, LocalDate.now(ZoneId.systemDefault()))) >
                    FAST_VAULT_PASSWORD_REMINDER_EVERY_N_DAYS)
        }

    override suspend fun setFastVaultPasswordReminderShownDate(vaultId: VaultId, date: LocalDate) {
        update(vaultId) { it.copy(fastVaultPasswordReminderShownDate = date) }
    }

    private suspend fun update(
        vaultId: VaultId,
        transform: (VaultMetadataEntity) -> VaultMetadataEntity,
    ) {
        dao.upsert(transform(getOrDefault(vaultId)))
    }

    private suspend fun getOrDefault(vaultId: VaultId) =
        dao.getBy(vaultId)
            ?: VaultMetadataEntity(vaultId = vaultId, fastVaultPasswordReminderShownDate = null)

    companion object {
        private const val FAST_VAULT_PASSWORD_REMINDER_EVERY_N_DAYS = 15
    }
}
