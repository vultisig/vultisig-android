package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.GLOBAL_REMINDER_STATUS_NEVER_SHOW
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import javax.inject.Inject

interface NeverShowGlobalBackupReminderUseCase : suspend () -> Unit

internal class NeverShowGlobalBackupReminderUseCaseImpl @Inject constructor(
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : NeverShowGlobalBackupReminderUseCase {
    override suspend fun invoke() {
        vaultDataStoreRepository.setGlobalBackupReminderStatus(GLOBAL_REMINDER_STATUS_NEVER_SHOW)
    }
}