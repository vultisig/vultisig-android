package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.models.Vault
import java.time.LocalDateTime
import javax.inject.Inject

fun interface CreateZipVaultBackupFileNameUseCase {
    operator fun invoke(vaults: List<Vault>): String
}

internal class CreateZipVaultBackupFileNameUseCaseImpl @Inject constructor() :
    CreateZipVaultBackupFileNameUseCase {
    override fun invoke(vaults: List<Vault>): String {
        val now = LocalDateTime.now()
        return "vaults_backup_${now.toLocalDate()}_${
            now.hour.toString().padStart(
                2,
                '0',
            )
        }${
            now.minute.toString().padStart(
                2,
                '0',
            )
        }${
            now.second.toString().padStart(
                2,
                '0',
            )
        }.zip"
    }
}
