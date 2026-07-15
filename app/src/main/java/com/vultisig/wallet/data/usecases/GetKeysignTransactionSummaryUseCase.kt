package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
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

    /**
     * A dApp-supplied transaction signed verbatim (e.g. an XRPL `signRipple` OfferCreate/Payment),
     * where the native `toAmount` is 0. [summary] is a pre-decoded one-line description of the real
     * operation so the notification banner reads the true terms instead of "Send 0 XRP".
     */
    data class DappTransaction(val summary: String) : KeysignTransactionSummary
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
                val signRipple = payload.signRipple
                when {
                    swap != null ->
                        KeysignTransactionSummary.Swap(
                            srcTokenValue = swap.srcTokenValue,
                            dstTicker = swap.dstToken.ticker,
                        )

                    // A dApp XRPL tx carries its real amounts in rawJson, not toAmount (which is
                    // 0);
                    // decode a readable summary so the banner doesn't say "Send 0 XRP".
                    signRipple != null ->
                        KeysignTransactionSummary.DappTransaction(
                            summary =
                                RippleDappTransactionDecoder.summarize(signRipple.rawJson)
                                    ?: "XRPL Transaction"
                        )

                    else ->
                        KeysignTransactionSummary.Send(
                            tokenValue = TokenValue(payload.toAmount, payload.coin)
                        )
                }
            }
            .onFailure { Timber.w(it, "Failed to parse keysign transaction summary") }
            .getOrNull()
}
