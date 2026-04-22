@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TonConnectSession
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.repositories.TonConnectRepository
import io.ktor.util.encodeBase64
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import vultisig.keysign.v1.SignTon

/**
 * Detects TonConnect-originated signing requests (KeysignPayload.sign_data == SignTon) inside a
 * decoded [KeysignMessageProto] and persists a [TonConnectSession] for the given vault.
 * Sub-issues #4147+ will flesh out dApp metadata and consumer logic.
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

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun invoke(message: KeysignMessageProto, vaultId: String) {
        val signTon = message.keysignPayload?.signTon ?: return
        val rawPayload = protoBuf.encodeToByteArray(SignTon.serializer(), signTon).encodeBase64()
        tonConnectRepository.saveSession(
            TonConnectSession(vaultId = vaultId, clientId = "", rawPayload = rawPayload)
        )
    }
}
