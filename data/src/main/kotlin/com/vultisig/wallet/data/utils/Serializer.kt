package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.api.models.JupiterSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.JupiterSwapQuoteError
import com.vultisig.wallet.data.api.models.KeysignResponseSerializable
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteError
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.QuoteSwapTotalDataJson
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.models.SplTokenResponseJson
import com.vultisig.wallet.data.api.models.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.cosmos.CosmosTHORChainAccountResponse
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountErrorJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountJson
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.models.SplTokenDeserialized.Error
import com.vultisig.wallet.data.models.SplTokenDeserialized.Result
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

interface DefaultSerializer<T> : KSerializer<T> {
    override fun serialize(encoder: Encoder, value: T) {
        throw UnsupportedOperationException("Serialization is not implemented")
    }
}

interface BigDecimalSerializer : DefaultSerializer<BigDecimal>

class BigDecimalSerializerImpl @Inject constructor() : BigDecimalSerializer {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigDecimal",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeDouble(value.toDouble())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal.valueOf(decoder.decodeDouble())
}

interface BigIntegerSerializer : DefaultSerializer<BigInteger>

class BigIntegerSerializerImpl @Inject constructor() : BigIntegerSerializer {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigInteger",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigInteger) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): BigInteger =
        BigInteger.valueOf(decoder.decodeLong())
}


interface SplTokenResponseJsonSerializer : DefaultSerializer<SplTokenDeserialized>


class SplTokenResponseJsonSerializerImpl @Inject constructor(
    private val json: Json,
) :
    SplTokenResponseJsonSerializer {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SplTokenResponseJsonSerializer")

    override fun deserialize(decoder: Decoder): SplTokenDeserialized {
        val input = decoder as JsonDecoder
        val jsonObject = input.decodeJsonElement().jsonObject

        return if (jsonObject.containsKey("error")) {
            Error(
                json.decodeFromJsonElement<SplTokenResponseJson>(jsonObject)
            )
        } else {
            Result(
                json.decodeFromJsonElement<Map<String, SplTokenJson>>(
                    jsonObject
                )
            )
        }
    }

}

interface ThorChainSwapQuoteResponseJsonSerializer :
    DefaultSerializer<THORChainSwapQuoteDeserialized>

class ThorChainSwapQuoteResponseJsonSerializerImpl @Inject constructor(private val json: Json) :
    ThorChainSwapQuoteResponseJsonSerializer {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("THORChainSwapQuoteResponseJsonSerializer")

    override fun deserialize(decoder: Decoder): THORChainSwapQuoteDeserialized {
        val input = decoder as JsonDecoder
        val jsonObject = input.decodeJsonElement().jsonObject

        return if (jsonObject.containsKey("fees")) {
            THORChainSwapQuoteDeserialized.Result(
                json.decodeFromJsonElement<THORChainSwapQuote>(jsonObject)
            )
        } else {
            THORChainSwapQuoteDeserialized.Error(
                json.decodeFromJsonElement<THORChainSwapQuoteError>(jsonObject)
            )
        }
    }
}

interface LiFiSwapQuoteResponseSerializer : DefaultSerializer<LiFiSwapQuoteDeserialized>

class LiFiSwapQuoteResponseSerializerImpl @Inject constructor(private val json: Json) :
    LiFiSwapQuoteResponseSerializer{
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("LiFiSwapQuoteResponseSerializer")

    override fun deserialize(decoder: Decoder): LiFiSwapQuoteDeserialized {
        val input = decoder as JsonDecoder
        val jsonObject = input.decodeJsonElement().jsonObject

        return if (jsonObject.containsKey("estimate")) {
            LiFiSwapQuoteDeserialized.Result(
                json.decodeFromJsonElement<LiFiSwapQuoteJson>(jsonObject)
            )
        } else {
            LiFiSwapQuoteDeserialized.Error(
                json.decodeFromJsonElement<LiFiSwapQuoteError>(jsonObject)
            )
        }
    }
}

interface OneInchSwapQuoteResponseJsonSerializer : DefaultSerializer<OneInchSwapQuoteDeserialized>

class OneInchSwapQuoteResponseJsonSerializerImpl @Inject constructor(private val json: Json) :
    OneInchSwapQuoteResponseJsonSerializer {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("OnceInchSwapQouteResponseJsonSerializer")

    override fun deserialize(decoder: Decoder): OneInchSwapQuoteDeserialized {
        val input = decoder as JsonDecoder
        val jsonObject = input.decodeJsonElement().jsonObject

        return if (jsonObject.containsKey("dstAmount")) {
            OneInchSwapQuoteDeserialized.Result(
                json.decodeFromJsonElement<OneInchSwapQuoteJson>(jsonObject)
            )
        } else {
            OneInchSwapQuoteDeserialized.Error(
                json.decodeFromJsonElement<String>(jsonObject)
            )
        }
    }
}

interface KeysignResponseSerializer : DefaultSerializer<tss.KeysignResponse>

class KeysignResponseSerializerImpl @Inject constructor() : KeysignResponseSerializer {
    private val serializer = KeysignResponseSerializable.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: tss.KeysignResponse) {
        val surrogate = KeysignResponseSerializable.serialize(value)
        encoder.encodeSerializableValue(serializer, surrogate)
    }

    override fun deserialize(decoder: Decoder): tss.KeysignResponse {
        val surrogate: KeysignResponseSerializable = decoder.decodeSerializableValue(serializer)
        return surrogate.toOriginal()
    }
}


interface CosmosThorChainResponseSerializer : DefaultSerializer<CosmosTHORChainAccountResponse>

class CosmosThorChainResponseSerializerImpl @Inject constructor(
    private val json: Json,
) :
    CosmosThorChainResponseSerializer {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CosmosThorChainResponseSerializer")

    override fun deserialize(decoder: Decoder): CosmosTHORChainAccountResponse {
        val input = decoder as JsonDecoder
        val jsonObject = input.decodeJsonElement().jsonObject

        val isErrorResponse = (jsonObject.containsKey("message")
                && jsonObject.containsKey("code"))

        return if (isErrorResponse) {
            CosmosTHORChainAccountResponse.Error(
                json.decodeFromJsonElement<THORChainAccountErrorJson>(jsonObject)
            )
        } else {
            CosmosTHORChainAccountResponse.Success(
                json.decodeFromJsonElement<THORChainAccountJson>(jsonObject)
            )
        }
    }
}