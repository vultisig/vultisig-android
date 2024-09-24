package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.models.SplTokenResponseJson
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.models.SplTokenDeserialized.*
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
import javax.inject.Singleton

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigDecimal",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeDouble(value.toDouble())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal.valueOf(decoder.decodeDouble())
}

object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigInteger",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigInteger) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): BigInteger =
        BigInteger(decoder.decodeInt().toString())
}

@Singleton
class SplTokenResponseJsonSerializer @Inject constructor(private val json: Json) :
    KSerializer<SplTokenDeserialized> {
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

    override fun serialize(encoder: Encoder, value: SplTokenDeserialized) {
        throw UnsupportedOperationException("Serialization is not required")
    }
}

