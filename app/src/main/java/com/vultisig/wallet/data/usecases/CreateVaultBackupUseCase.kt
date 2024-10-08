@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import javax.inject.Inject

internal interface CreateVaultBackupUseCase : (VaultProto, String?) -> String?

internal class CreateVaultBackupUseCaseImpl @Inject constructor(
    private val encryption: Encryption,
    private val protoBuf: ProtoBuf,
) : CreateVaultBackupUseCase {

    override fun invoke(
        vault: VaultProto,
        password: String?
    ): String? {
        val vaultBytes = protoBuf.encodeToByteArray(vault)

        val contentBytes = if (password != null) {
            try {
                encryption.encrypt(vaultBytes, password.toByteArray())
            } catch (e: Exception) {
                return null
            }
        } else {
            vaultBytes
        }.encodeBase64()

        return protoBuf.encodeToByteArray(
            VaultContainerProto(
                version = VAULT_BACKUP_VERSION,
                vault = contentBytes,
                isEncrypted = password != null
            )
        ).encodeBase64()
    }

    companion object {
        private const val VAULT_BACKUP_VERSION = 1uL
    }

}