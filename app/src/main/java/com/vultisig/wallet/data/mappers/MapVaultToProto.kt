package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.KeyShareProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import google.protobuf.Timestamp
import kotlinx.datetime.Clock
import javax.inject.Inject

internal interface MapVaultToProto : MapperFunc<Vault, VaultProto>

internal class MapVaultToProtoImpl @Inject constructor() : MapVaultToProto {

    override fun invoke(from: Vault) = VaultProto(
        name = from.name,
        localPartyId = from.localPartyID,
        publicKeyEcdsa = from.pubKeyECDSA,
        publicKeyEddsa = from.pubKeyEDDSA,
        hexChainCode = from.hexChainCode,
        signers = from.signers,
        resharePrefix = from.resharePrefix,
        keyShares = from.keyshares.map {
            KeyShareProto(
                publicKey = it.pubKey,
                keyshare = it.keyShare
            )
        },
        createdAt = Timestamp(Clock.System.now().epochSeconds),
    )

}