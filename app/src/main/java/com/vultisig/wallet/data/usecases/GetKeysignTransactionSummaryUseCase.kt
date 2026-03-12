package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import io.ktor.util.decodeBase64Bytes
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

internal sealed interface KeysignTransactionSummary {
    data class Swap(val srcTokenValue: TokenValue, val dstTicker: String) :
        KeysignTransactionSummary

    data class Send(val tokenValue: TokenValue) : KeysignTransactionSummary
}

internal interface GetKeysignTransactionSummaryUseCase :
    suspend (String) -> KeysignTransactionSummary?

internal class GetKeysignTransactionSummaryUseCaseImpl
@Inject
constructor(
    @OptIn(ExperimentalSerializationApi::class) private val protoBuf: ProtoBuf,
    private val decompressQr: DecompressQrUseCase,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
) : GetKeysignTransactionSummaryUseCase {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun invoke(qrCodeData: String): KeysignTransactionSummary? =
        runCatching {
                val jsonData = DeepLinkHelper(qrCodeData).getJsonData() ?: return null
                val rawBytes = decompressQr(jsonData.decodeBase64Bytes())
                val proto = protoBuf.decodeFromByteArray(KeysignMessageProto.serializer(), rawBytes)

                if (proto.keysignPayload == null) return null

                val payload = mapKeysignMessageFromProto(proto).payload ?: return null

                val swap = payload.swapPayload
                if (swap != null) {
                    KeysignTransactionSummary.Swap(
                        srcTokenValue = swap.srcTokenValue,
                        dstTicker = swap.dstToken.ticker,
                    )
                } else {
                    KeysignTransactionSummary.Send(
                        tokenValue = TokenValue(payload.toAmount, payload.coin)
                    )
                }
            }
            .onFailure { Timber.w(it, "Failed to parse keysign transaction summary") }
            .getOrNull()
}
