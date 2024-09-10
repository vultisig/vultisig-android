package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.KeysignMessage
import javax.inject.Inject

internal interface KeysignMessageFromProtoMapper : SuspendMapperFunc<KeysignMessageProto, KeysignMessage>

internal class KeysignMessageFromProtoMapperImpl @Inject constructor(
    private val mapKeysignPayload: KeysignPayloadProtoMapper,
) : KeysignMessageFromProtoMapper {

    override suspend fun invoke(from: KeysignMessageProto): KeysignMessage = KeysignMessage(
        sessionID = from.sessionId,
        serviceName = from.serviceName,
        payload = mapKeysignPayload(requireNotNull(from.keysignPayload)),
        encryptionKeyHex = from.encryptionKeyHex,
        useVultisigRelay = from.useVultisigRelay,
    )

}
