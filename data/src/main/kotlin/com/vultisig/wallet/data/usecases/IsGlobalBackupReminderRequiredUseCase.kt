package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.GLOBAL_REMINDER_STATUS_NEVER_SHOW
import com.vultisig.wallet.data.repositories.GLOBAL_REMINDER_STATUS_NOT_SET
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.utils.VultiDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface IsGlobalBackupReminderRequiredUseCase : suspend () -> Boolean

internal class IsGlobalBackupReminderRequiredUseCaseImpl @Inject constructor(
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : IsGlobalBackupReminderRequiredUseCase {
    override suspend fun invoke(): Boolean {
        val shownEpochMonth = vaultDataStoreRepository.readGlobalBackupReminderStatus().first()
        val currentEpochMonth = VultiDate.getEpochMonth()
        when (shownEpochMonth) {
            GLOBAL_REMINDER_STATUS_NOT_SET -> {
                vaultDataStoreRepository.setGlobalBackupReminderStatus(currentEpochMonth)
                return true
            }
            GLOBAL_REMINDER_STATUS_NEVER_SHOW -> {
                return false
            }
            currentEpochMonth -> {
                return false
            }
            else -> {
                vaultDataStoreRepository.setGlobalBackupReminderStatus(currentEpochMonth)
                return true
            }
        }
    }
}