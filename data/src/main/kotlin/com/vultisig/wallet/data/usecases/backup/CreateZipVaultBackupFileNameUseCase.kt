package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.models.Vault
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

fun interface CreateZipVaultBackupFileNameUseCase {
    operator fun invoke(vaults: List<Vault>): String
}

internal class CreateZipVaultBackupFileNameUseCaseImpl @Inject constructor() :
    CreateZipVaultBackupFileNameUseCase {
    override fun invoke(vaults: List<Vault>): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        // Sample Result: vaults_backup_2025-10-21_143022.zip
        return "vaults_backup_${now.date}_${now.hour}${now.minute}${now.second}.zip"
    }
}
