package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import io.ktor.util.decodeBase64Bytes
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

internal interface GetKeysignTransactionSummaryUseCase : suspend (String) -> String?

internal class GetKeysignTransactionSummaryUseCaseImpl
@Inject
constructor(
    @OptIn(ExperimentalSerializationApi::class) private val protoBuf: ProtoBuf,
    private val decompressQr: DecompressQrUseCase,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
) : GetKeysignTransactionSummaryUseCase {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun invoke(qrCodeData: String): String? =
        runCatching {
                val jsonData = DeepLinkHelper(qrCodeData).getJsonData() ?: return null
                val rawBytes = decompressQr(jsonData.decodeBase64Bytes())
                val proto = protoBuf.decodeFromByteArray(KeysignMessageProto.serializer(), rawBytes)

                if (proto.keysignPayload == null) return null

                val payload = mapKeysignMessageFromProto(proto).payload ?: return null

                val swap = payload.swapPayload
                if (swap != null) {
                    val amount = formatAmount(swap.srcTokenValue)
                    "Swap $amount → ${swap.dstToken.ticker}"
                } else {
                    val amount = formatAmount(TokenValue(payload.toAmount, payload.coin))
                    "Send $amount"
                }
            }
            .onFailure { Timber.w(it, "Failed to parse keysign transaction summary") }
            .getOrNull()

    private fun formatAmount(tokenValue: TokenValue): String {
        val formatted =
            tokenValue.decimal
                .setScale(8, RoundingMode.DOWN)
                .stripTrailingZeros()
                .let { if (it.scale() < 0) it.setScale(0) else it }
                .toPlainString()
        return "$formatted ${tokenValue.unit}"
    }
}
