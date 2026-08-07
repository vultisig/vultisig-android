package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.ChainPublicKeyProto
import com.vultisig.wallet.data.models.proto.v1.KeyShareProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import google.protobuf.Timestamp
import javax.inject.Inject
import kotlinx.datetime.Clock

internal interface MapVaultToProto : MapperFunc<Vault, VaultProto>

internal class MapVaultToProtoImpl @Inject constructor() : MapVaultToProto {

    override fun invoke(from: Vault): VaultProto {
        // A vault always has keyshares; an empty list here means they were dropped on the way out
        // of storage because the data key was unavailable. Exporting that would hand the user a
        // .vult that reports success, restores cleanly, and can never sign — the worst shape a
        // backup failure can take, because it is only discovered when the backup is needed.
        check(from.keyshares.isNotEmpty()) { "Refusing to export a vault with no keyshares" }
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
            createdAt = Timestamp(Clock.System.now().epochSeconds),
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
