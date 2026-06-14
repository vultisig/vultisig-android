@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.RouterApi
import com.vultisig.wallet.data.mappers.PayloadToProtoMapper
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import io.ktor.util.encodeBase64
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload

/**
 * Builds the compressed, base64-encoded keysign QR payload (the `jsonData` portion of the deep
 * link).
 *
 * Extracted verbatim from `KeysignFlowViewModel.updateKeysignPayload` — the proto encoding, QR
 * compression, and optional relay upload of oversized payloads. The ViewModel keeps the
 * coordination (starting the mediator/session, building the deep-link URL, notifying devices); this
 * use case owns the proto/QR work so the related deps (`ProtoBuf`, `PayloadToProtoMapper`,
 * `RouterApi`, `CompressQrUseCase`) leave the ViewModel constructor.
 */
internal class BuildKeysignMessageUseCase
@Inject
constructor(
    private val protoBuf: ProtoBuf,
    private val payloadToProtoMapper: PayloadToProtoMapper,
    private val routerApi: RouterApi,
    private val compressQr: CompressQrUseCase,
) {
    suspend operator fun invoke(
        keysignPayload: KeysignPayload?,
        customMessagePayload: CustomMessagePayload?,
        sessionId: String,
        serviceName: String,
        encryptionKeyHex: String,
        serverAddress: String,
        useVultisigRelay: Boolean,
    ): String {
        val keysignPayloadProto = payloadToProtoMapper(keysignPayload)

        val keysignProto =
            protoBuf.encodeToByteArray(
                KeysignMessageProto(
                    sessionId = sessionId,
                    serviceName = serviceName,
                    keysignPayload = keysignPayloadProto,
                    encryptionKeyHex = encryptionKeyHex,
                    useVultisigRelay = useVultisigRelay,
                    customMessagePayload = customMessagePayload,
                )
            )

        Timber.d("keysignProto: $keysignProto")

        var data = compressQr(keysignProto).encodeBase64()
        if (keysignPayloadProto != null && routerApi.shouldUploadPayload(data)) {
            protoBuf.encodeToByteArray(keysignPayloadProto).let {
                compressQr(it).encodeBase64().let { compressedData ->
                    val hash = routerApi.uploadPayload(serverAddress, compressedData)
                    protoBuf
                        .encodeToByteArray(
                            KeysignMessageProto(
                                sessionId = sessionId,
                                serviceName = serviceName,
                                encryptionKeyHex = encryptionKeyHex,
                                useVultisigRelay = useVultisigRelay,
                                customMessagePayload = customMessagePayload,
                                payloadId = hash,
                            )
                        )
                        .let { compressedData -> data = compressQr(compressedData).encodeBase64() }
                }
            }
        }
        return data
    }
}
