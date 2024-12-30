package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.KeygenMessage
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import com.vultisig.wallet.data.models.proto.v1.toSigningLibType
import javax.inject.Inject

interface KeygenMessageFromProtoMapper : MapperFunc<KeygenMessageProto, KeygenMessage>

internal class KeygenMessageFromProtoMapperImpl @Inject constructor() :
    KeygenMessageFromProtoMapper {
    override fun invoke(from: KeygenMessageProto) = KeygenMessage(
        vaultName = from.vaultName,
        sessionID = from.sessionId,
        hexChainCode = from.hexChainCode,
        serviceName = from.serviceName,
        encryptionKeyHex = from.encryptionKeyHex,
        useVultisigRelay = from.useVultisigRelay,
        libType = from.libType.toSigningLibType(),
    )
}

internal interface KeygenMessageToProtoMapper : MapperFunc<KeygenMessage, KeygenMessageProto>

internal class KeygenMessageToProtoMapperImpl @Inject constructor() : KeygenMessageToProtoMapper {
    override fun invoke(from: KeygenMessage) = KeygenMessageProto(
        vaultName = from.vaultName,
        sessionId = from.sessionID,
        hexChainCode = from.hexChainCode,
        serviceName = from.serviceName,
        encryptionKeyHex = from.encryptionKeyHex,
        useVultisigRelay = from.useVultisigRelay,
        libType = from.libType.toProto(),
    )
}