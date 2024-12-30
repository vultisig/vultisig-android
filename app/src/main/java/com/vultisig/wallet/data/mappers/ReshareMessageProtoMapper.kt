package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.ReshareMessage
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import com.vultisig.wallet.data.models.proto.v1.toSigningLibType
import javax.inject.Inject

internal interface ReshareMessageFromProtoMapper : MapperFunc<ReshareMessageProto, ReshareMessage>

internal class ReshareMessageFromProtoMapperImpl @Inject constructor() :
    ReshareMessageFromProtoMapper {
    override fun invoke(from: ReshareMessageProto) = ReshareMessage(
        sessionID = from.sessionId,
        hexChainCode = from.hexChainCode,
        serviceName = from.serviceName,
        pubKeyECDSA = from.publicKeyEcdsa,
        oldParties = from.oldParties,
        encryptionKeyHex = from.encryptionKeyHex,
        useVultisigRelay = from.useVultisigRelay,
        oldResharePrefix = from.oldResharePrefix,
        vaultName = from.vaultName,
        libType = from.libType.toSigningLibType(),
    )
}

internal interface ReshareMessageToProtoMapper : MapperFunc<ReshareMessage, ReshareMessageProto>

internal class ReshareMessageToProtoMapperImpl @Inject constructor() : ReshareMessageToProtoMapper {
    override fun invoke(from: ReshareMessage) = ReshareMessageProto(
        sessionId = from.sessionID,
        hexChainCode = from.hexChainCode,
        serviceName = from.serviceName,
        publicKeyEcdsa = from.pubKeyECDSA,
        oldParties = from.oldParties,
        encryptionKeyHex = from.encryptionKeyHex,
        useVultisigRelay = from.useVultisigRelay,
        oldResharePrefix = from.oldResharePrefix,
        vaultName = from.vaultName,
        libType = from.libType.toProto(),
    )
}