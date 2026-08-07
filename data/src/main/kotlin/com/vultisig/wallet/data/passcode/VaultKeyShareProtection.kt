package com.vultisig.wallet.data.passcode

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.KeyShareEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Bulk-converts stored keyshares between plaintext and passcode-encrypted when the user turns the
 * passcode on or off.
 *
 * Both directions are safe to interrupt. Rows carry their own "is encrypted" marker, so a process
 * death mid-run leaves a readable mix rather than a corrupt table, and re-running finishes the job.
 * The caller is responsible for ordering the credential write around these calls so that a partial
 * run never produces ciphertext whose key has already been discarded — see
 * [PasscodeRepositoryImpl].
 */
internal interface VaultKeyShareProtection {

    /** Encrypts every plaintext keyshare under [dataKey]. */
    suspend fun protectAll(dataKey: ByteArray)

    /**
     * True when at least one stored keyshare is encrypted. Lets the repository tell "no passcode
     * was ever set" apart from "the passcode material is gone but its ciphertext is still here".
     */
    suspend fun hasEncryptedKeyShares(): Boolean

    /**
     * Decrypts every encrypted keyshare with [dataKey] and stores it in the clear.
     *
     * @throws IllegalStateException if a keyshare cannot be decrypted, leaving the table untouched
     *   rather than dropping shares the user would need to sign.
     */
    suspend fun unprotectAll(dataKey: ByteArray)
}

internal class VaultKeyShareProtectionImpl
@Inject
constructor(
    private val vaultDao: VaultDao,
    private val cipher: KeyShareCipher,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VaultKeyShareProtection {

    override suspend fun hasEncryptedKeyShares(): Boolean =
        withContext(dispatcher) {
            vaultDao.loadAllKeyShares().any { cipher.isEncrypted(it.keyShare) }
        }

    override suspend fun protectAll(dataKey: ByteArray) {
        withContext(dispatcher) {
            val plaintext =
                vaultDao.loadAllKeyShares().filterNot { cipher.isEncrypted(it.keyShare) }
            if (plaintext.isEmpty()) return@withContext
            vaultDao.upsertKeyshares(
                plaintext.map {
                    it.copy(keyShare = cipher.encrypt(it.keyShare, dataKey, it.identity()))
                }
            )
            Timber.i("Encrypted %d keyshare(s) at rest", plaintext.size)
        }
    }

    override suspend fun unprotectAll(dataKey: ByteArray) {
        withContext(dispatcher) {
            val encrypted = vaultDao.loadAllKeyShares().filter { cipher.isEncrypted(it.keyShare) }
            if (encrypted.isEmpty()) return@withContext
            // Decrypt everything before writing anything: a share that will not open must abort the
            // whole operation while the passcode is still in place to protect the rest.
            val decrypted =
                encrypted.map { entity ->
                    val plaintext =
                        checkNotNull(cipher.decrypt(entity.keyShare, dataKey, entity.identity())) {
                            "Keyshare for vault ${entity.vaultId} failed to decrypt"
                        }
                    entity.copy(keyShare = plaintext)
                }
            vaultDao.upsertKeyshares(decrypted)
            Timber.i("Decrypted %d keyshare(s) back to plaintext", decrypted.size)
        }
    }
}

internal fun KeyShareEntity.identity(): KeyShareIdentity =
    KeyShareIdentity(vaultId = vaultId, pubKey = pubKey)
