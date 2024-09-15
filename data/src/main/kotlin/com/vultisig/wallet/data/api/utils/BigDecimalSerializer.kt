package com.vultisig.wallet.data.api.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.BigInteger

internal object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigDecimal",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeDouble(value.toDouble())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal.valueOf(decoder.decodeDouble())
}

internal object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigInteger",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigInteger) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): BigInteger =
        BigInteger(decoder.decodeInt().toString())
}