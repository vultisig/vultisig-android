package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import javax.inject.Inject

fun interface CreateVaultBackupFileNameUseCase {
    operator fun invoke(vault: Vault): String
}

internal class CreateVaultBackupFileNameUseCaseImpl @Inject constructor() :
    CreateVaultBackupFileNameUseCase {
    override fun invoke(vault: Vault): String {
        val shareNamePart = when (vault.libType) {
            SigningLibType.GG20 -> "part"
            SigningLibType.DKLS, SigningLibType.KeyImport -> "share"
        }

        val fileName =
            "${vault.name}-${vault.pubKeyECDSA.takeLast(4)}" +
                    "-$shareNamePart${vault.getVaultPart()}of${vault.signers.size}.vult"

        return fileName
    }
}
