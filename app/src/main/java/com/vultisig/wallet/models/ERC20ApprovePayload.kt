package com.vultisig.wallet.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.data.mappers.fromIosBigInt
import com.vultisig.wallet.data.mappers.toIosBigInt
import java.lang.reflect.Type
import java.math.BigInteger

internal data class ERC20ApprovePayload(
    @SerializedName("amount")
    val amount: BigInteger,
    @SerializedName("spender")
    val spender: String,
)

internal class ERC20ApprovePayloadSerializer : JsonSerializer<ERC20ApprovePayload> {
    override fun serialize(
        src: ERC20ApprovePayload,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = JsonObject().apply {
        add("amount", src.amount.toIosBigInt())
        addProperty("spender", src.spender)
    }
}

internal class ERC20ApprovePayloadDeserializer : JsonDeserializer<ERC20ApprovePayload> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): ERC20ApprovePayload {
        val jo = json.asJsonObject

        return ERC20ApprovePayload(
            amount = jo["amount"].fromIosBigInt(),
            spender = jo["spender"].asString,
        )
    }
}