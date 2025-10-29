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
        return "vaults_backup_${now.date}_${
            now.hour.toString().padStart(
                2,
                '0'
            )
        }${
            now.minute.toString().padStart(
                2,
                '0'
            )
        }${
            now.second.toString().padStart(
                2,
                '0'
            )
        }.zip"
    }
}
