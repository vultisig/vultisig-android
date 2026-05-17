@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.data.utils.runCatchingCancellable
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
        val effectivePassword = password?.takeUnless { it.isBlank() }

        val payload =
            if (effectivePassword == null) vaultBytes
            else
                runCatchingCancellable {
                        encryption.encrypt(vaultBytes, effectivePassword.toByteArray())
                    }
                    .onFailure { Timber.e(it, "Vault backup encryption failed") }
                    .getOrNull() ?: return null

        val backup =
            protoBuf
                .encodeToByteArray(
                    VaultContainerProto(
                        version = VAULT_BACKUP_VERSION,
                        vault = payload.encodeBase64(),
                        isEncrypted = effectivePassword != null,
                    )
                )
                .encodeBase64()

        // Round-trip the just-produced backup with the same password before handing it back, so
        // we never return a file the user couldn't restore. Catches encryption-layer regressions
        // and container-encoding drift at export time instead of weeks later.
        if (!isRecoverable(backup, vaultBytes, effectivePassword)) {
            Timber.e("Vault backup self-check failed; refusing to return an unrecoverable backup")
            return null
        }
        return backup
    }

    private fun isRecoverable(
        backup: String,
        expectedVaultBytes: ByteArray,
        effectivePassword: String?,
    ): Boolean =
        runCatchingCancellable {
                val container =
                    protoBuf.decodeFromByteArray<VaultContainerProto>(backup.decodeBase64Bytes())
                // Defend against metadata drift: container.isEncrypted must agree with the
                // password we used to produce it, otherwise the round-trip would silently take
                // the wrong recovery branch and could pass on a semantically broken file.
                if (container.isEncrypted != (effectivePassword != null)) {
                    return@runCatchingCancellable false
                }
                val payload = container.vault.decodeBase64Bytes()
                val recovered =
                    effectivePassword?.let { encryption.decrypt(payload, it.toByteArray()) }
                        ?: payload
                recovered?.contentEquals(expectedVaultBytes) == true
            }
            .onFailure { Timber.e(it, "Vault backup self-check raised an exception") }
            .getOrDefault(false)

    companion object {
        private const val VAULT_BACKUP_VERSION = 1uL
    }
}
