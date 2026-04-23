@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TonKeysignSession
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.repositories.TonConnectRepository
import java.util.Base64
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import vultisig.keysign.v1.SignTon

/**
 * Detects TON signing requests ([KeysignPayload.signTon] present) in a [KeysignMessageProto] and
 * persists a [TonKeysignSession] keyed by vault ID for later TonConnect consumer use.
 */
internal interface PersistTonConnectSessionUseCase {
    suspend operator fun invoke(message: KeysignMessageProto, vaultId: String)
}

internal class PersistTonConnectSessionUseCaseImpl
@Inject
constructor(
    private val tonConnectRepository: TonConnectRepository,
    private val protoBuf: ProtoBuf,
) : PersistTonConnectSessionUseCase {

    override suspend fun invoke(message: KeysignMessageProto, vaultId: String) {
        val signTon = message.keysignPayload?.signTon ?: return
        val signTonProtoBase64 =
            Base64.getEncoder()
                .encodeToString(protoBuf.encodeToByteArray(SignTon.serializer(), signTon))
        tonConnectRepository.saveSession(
            TonKeysignSession(vaultId = vaultId, signTonProtoBase64 = signTonProtoBase64)
        )
    }
}
