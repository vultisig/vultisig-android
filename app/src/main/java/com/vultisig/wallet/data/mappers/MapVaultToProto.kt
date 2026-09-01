package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.ChainPublicKeyProto
import com.vultisig.wallet.data.models.proto.v1.KeyShareProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import google.protobuf.Timestamp
import java.time.Instant
import javax.inject.Inject

internal interface MapVaultToProto : MapperFunc<Vault, VaultProto>

/**
 * A vault reached export with no keyshares, which means storage dropped them because the data key
 * was unavailable — not that the vault has none.
 *
 * Typed rather than a bare `check`, because every export path runs inside a plain
 * `viewModelScope.launch`: an [IllegalStateException] there kills the coroutine before the backup
 * flow can report the failure, delete the empty document it already created, or tell the user
 * anything. Callers catch this one type and nothing else.
 */
internal class VaultKeysharesUnavailableException :
    IllegalStateException("Refusing to export a vault with no keyshares")

/**
 * Maps [vault] for export, or returns null when its keyshares are unavailable.
 *
 * Null is the failure channel the backup paths already speak — `createVaultBackup` returns it too,
 * and every caller answers it by deleting the document it created and showing the user an error.
 * Catching the one type keeps cancellation and genuine bugs propagating.
 */
internal fun MapVaultToProto.exportableOrNull(vault: Vault): VaultProto? =
    try {
        this(vault)
    } catch (_: VaultKeysharesUnavailableException) {
        null
    }

internal class MapVaultToProtoImpl @Inject constructor() : MapVaultToProto {

    override fun invoke(from: Vault): VaultProto {
        // A vault always has keyshares; an empty list here means they were dropped on the way out
        // of storage because the data key was unavailable. Exporting that would hand the user a
        // .vult that reports success, restores cleanly, and can never sign — the worst shape a
        // backup failure can take, because it is only discovered when the backup is needed.
        //
        // Empty is enough to test for because the read is all-or-nothing: a vault that could not
        // be fully decrypted arrives with none of its shares rather than the ones that happened to
        // open. Storage owns that guarantee — see VaultRepositoryImpl.toKeyShares — because the
        // count of shares a vault *should* have exists only there.
        if (from.keyshares.isEmpty()) throw VaultKeysharesUnavailableException()
        return VaultProto(
            name = from.name,
            localPartyId = from.localPartyID,
            publicKeyEcdsa = from.pubKeyECDSA,
            publicKeyEddsa = from.pubKeyEDDSA,
            hexChainCode = from.hexChainCode,
            signers = from.signers,
            resharePrefix = from.resharePrefix,
            keyShares =
                from.keyshares.map { KeyShareProto(publicKey = it.pubKey, keyshare = it.keyShare) },
            createdAt = Timestamp(Instant.now().epochSecond),
            libType = from.libType.toProto(),
            chainPublicKeys =
                from.chainPublicKeys.map {
                    ChainPublicKeyProto(
                        publicKey = it.publicKey,
                        chain = it.chain,
                        isEddsa = it.isEddsa,
                    )
                },
            publicKeyMldsa44 = from.pubKeyMLDSA,
        )
    }
}
