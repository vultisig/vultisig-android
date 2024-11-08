package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import javax.inject.Inject

interface NeverShowGlobalBackupReminder : suspend () -> Unit

internal class NeverShowGlobalBackupReminderImpl @Inject constructor(
    val vaultDataStoreRepository: VaultDataStoreRepository,
) : NeverShowGlobalBackupReminder {
    override suspend fun invoke() {
        vaultDataStoreRepository.setGlobalBackupReminderStatus(-1)
    }
}