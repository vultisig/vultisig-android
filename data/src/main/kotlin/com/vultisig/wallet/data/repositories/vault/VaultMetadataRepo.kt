package com.vultisig.wallet.data.repositories.vault

import com.vultisig.wallet.data.db.dao.VaultMetadataDao
import com.vultisig.wallet.data.db.models.VaultMetadataEntity
import com.vultisig.wallet.data.models.VaultId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.math.abs

interface VaultMetadataRepo {

    suspend fun isFastVaultPasswordReminderRequired(vaultId: VaultId): Boolean

    suspend fun setFastVaultPasswordReminderShownDate(vaultId: VaultId, date: LocalDate)

}

internal class VaultMetadataRepoImpl @Inject constructor(
    private val dao: VaultMetadataDao,
) : VaultMetadataRepo {

    override suspend fun isFastVaultPasswordReminderRequired(vaultId: VaultId): Boolean =
        getOrDefault(vaultId).fastVaultPasswordReminderShownDate.let {
            it == null || (abs(it.daysUntil(Clock.System.todayIn(TimeZone.currentSystemDefault()))) >
                    FAST_VAULT_PASSWORD_REMINDER_EVERY_N_DAYS)
        }

    override suspend fun setFastVaultPasswordReminderShownDate(vaultId: VaultId, date: LocalDate) {
        update(vaultId) {
            it.copy(
                fastVaultPasswordReminderShownDate = date,
            )
        }
    }

    private suspend fun update(
        vaultId: VaultId,
        transform: (VaultMetadataEntity) -> VaultMetadataEntity
    ) {
        dao.upsert(
            transform(getOrDefault(vaultId))
        )
    }

    private suspend fun getOrDefault(vaultId: VaultId) =
        dao.getBy(vaultId) ?: VaultMetadataEntity(
            vaultId = vaultId,
            fastVaultPasswordReminderShownDate = null,
        )

    companion object {
        private const val FAST_VAULT_PASSWORD_REMINDER_EVERY_N_DAYS = 15
    }

}