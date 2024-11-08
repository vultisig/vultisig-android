package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.utils.VultiDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface GetGlobalBackupReminderStatus : suspend () -> Boolean

internal class GetGlobalBackupReminderStatusImpl @Inject constructor(
    val vaultDataStoreRepository: VaultDataStoreRepository,
) : GetGlobalBackupReminderStatus {
    override suspend fun invoke(): Boolean {
        val shownMonth = vaultDataStoreRepository.readGlobalBackupReminderStatus().first()
        val currentEpochMonth = VultiDate.getEpochMonth()
        when (shownMonth) {
            0 -> {
                vaultDataStoreRepository.setGlobalBackupReminderStatus(currentEpochMonth)
                return true
            }
            -1 -> {
                return false
            }
            currentEpochMonth -> {
                vaultDataStoreRepository.setGlobalBackupReminderStatus(currentEpochMonth)
                return false
            }
            else -> {
                vaultDataStoreRepository.setGlobalBackupReminderStatus(currentEpochMonth)
                return true
            }
        }
    }
}