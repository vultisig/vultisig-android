@file:UseSerializers(BigDecimalSerializer::class)
package com.vultisig.wallet.data.api.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal


@Serializable
data class CmcPriceResponseJson(
    @SerialName("data")
    val data: Map<String, TokenPriceJson>
)

@Serializable
data class TokenPriceJson(
    @SerialName("quote")
    val quote: Map<String, QuoteJson>
)

@Serializable
data class QuoteJson(
    @SerialName("price")
    val price: BigDecimal
)

private object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor(
        "BigDecimal",
        PrimitiveKind.DOUBLE
    )

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeDouble(value.toDouble())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal.valueOf(decoder.decodeDouble())
}