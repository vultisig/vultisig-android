@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

const val MIME_TYPE_VAULT = "application/octet-stream"

internal interface CreateVaultBackupUseCase : (VaultProto, String?) -> String?

internal class CreateVaultBackupUseCaseImpl
@Inject
constructor(private val encryption: VaultBackupEncryption, private val protoBuf: ProtoBuf) :
    CreateVaultBackupUseCase {

    override fun invoke(vault: VaultProto, password: String?): String? {
        val vaultBytes = protoBuf.encodeToByteArray(vault)

        val contentBytes =
            if (!password.isNullOrBlank()) {
                    try {
                        encryption.encrypt(vaultBytes, password.toByteArray())
                    } catch (e: Exception) {
                        Timber.e(e)
                        return null
                    }
                } else {
                    vaultBytes
                }
                .encodeBase64()

        val backup =
            protoBuf
                .encodeToByteArray(
                    VaultContainerProto(
                        version = VAULT_BACKUP_VERSION,
                        vault = contentBytes,
                        isEncrypted = !password.isNullOrBlank(),
                    )
                )
                .encodeBase64()

        // Round-trip the just-produced backup with the same password before handing it back, so
        // we never return a file the user couldn't restore. Catches encryption layer regressions
        // and container-encoding drift at export time instead of weeks later.
        if (!isRecoverable(backup, vaultBytes, password)) {
            Timber.e("Vault backup self-check failed; refusing to return a backup we can't decrypt")
            return null
        }
        return backup
    }

    private fun isRecoverable(
        backup: String,
        expectedVaultBytes: ByteArray,
        password: String?,
    ): Boolean =
        try {
            val container =
                protoBuf.decodeFromByteArray<VaultContainerProto>(backup.decodeBase64Bytes())
            val payload = container.vault.decodeBase64Bytes()
            val recovered =
                if (!password.isNullOrBlank()) {
                    encryption.decrypt(payload, password.toByteArray())
                } else {
                    payload
                }
            recovered != null && recovered.contentEquals(expectedVaultBytes)
        } catch (e: Exception) {
            Timber.e(e, "Vault backup self-check raised an exception")
            false
        }

    companion object {
        private const val VAULT_BACKUP_VERSION = 1uL
    }
}
